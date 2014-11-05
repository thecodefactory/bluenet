package com.nm.bluenetcommon;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;

public class BlueNetNetworkCommon
{
    private static final String TAG = "BlueNetNetworkCommon";

    private BlueNetLog Log = null;
    private Selector selector = null;
    private Indexer indexClass = null;
    private ByteBuffer readBuffer = null;
    private ObjectManager channelManager = null;
    private HTTPSTagManager httpsTagManager = null;
    private Vector<SelectionKey> throttledKeyList = null;
    private PacketQueueManager packetQueueManager = null;
    private BlueNetPacketAllocator bnPacketAllocator = null;
    private Vector<LinkedBlockingQueue<BlueNetPacket>> packetSendQueueVec = null;
    private Vector<LinkedBlockingQueue<Integer>> closedTagQueueVec = null;
    private Vector<BlueNetPacket> sendPackets = null;
    private Vector<BlueNetPacket> finalSendPackets = null;
    private BlueNetPacket[] tmpPacketArray = null;
    private byte[] tmpData = null;

    public BlueNetNetworkCommon(
        BlueNetLog Log, Indexer indexClass, ObjectManager channelManager, Selector selector,
        HTTPSTagManager httpsTagManager, PacketQueueManager packetQueueManager,
        Vector<LinkedBlockingQueue<BlueNetPacket>> packetSendQueueVec,
        Vector<LinkedBlockingQueue<Integer>> closedTagQueueVec,
        BlueNetPacketAllocator bnPacketAllocator)
    {
        this.Log = Log;
        this.indexClass = indexClass;
        this.channelManager = channelManager;
        this.selector = selector;
        this.httpsTagManager = httpsTagManager;
        this.packetQueueManager = packetQueueManager;
        this.packetSendQueueVec = packetSendQueueVec;
        this.closedTagQueueVec = closedTagQueueVec;
        this.bnPacketAllocator = bnPacketAllocator;

        this.throttledKeyList = new Vector<SelectionKey>();
        this.readBuffer = ByteBuffer.allocate(BlueNetPacket.MAX_DATA_LEN);

        this.sendPackets = new Vector<BlueNetPacket>(
            BlueNetPacketAllocator.BN_MAX_SEND_QUEUE_LEN);
        this.finalSendPackets = new Vector<BlueNetPacket>(
            BlueNetPacketAllocator.BN_MAX_SEND_QUEUE_LEN);
        this.tmpPacketArray =
            new BlueNetPacket[BlueNetPacketAllocator.BN_MAX_SEND_QUEUE_LEN];
        this.tmpData = new byte[BlueNetPacket.MAX_DATA_LEN];
    }

    public void updateQueueSize(int queueSize)
    {
        // we track the queue size since if it becomes below our throttle
        // threshold, we can wake up any throttled keys for more reading
        if (queueSize < BlueNetPacketAllocator.BN_MAX_SEND_QUEUE_LEN)
        {
            // but be careful to only wake up the selector if we have
            // keys that were throttled (otherwise it's a CPU waste of time)
            if (this.throttledKeyList.size() > 0)
            {
                this.selector.wakeup();
            }
        }
    }

    public void disposePacket(BlueNetPacket curPacket)
    {
        this.disposePacket(curPacket, curPacket.shouldClose());
    }

    public void disposePacket(BlueNetPacket curPacket, boolean shouldClose)
    {
//        Log.d(TAG, "DISPOSE PACKET CALLED ON " + curPacket);
        if (shouldClose == true)
        {
            this.teardownSocketChannelMappings(curPacket.getTag());
        }

        if (curPacket.isPooled())
        {
            this.bnPacketAllocator.returnPacketToPool(curPacket);
//            Log.d(TAG, "Packet Returned to Pool.  SIZE = " +
//                this.bnPacketAllocator.getPacketPoolSize());
        }
    }

    synchronized public void clearClosedTagsIfAny(
        BlueNetPacket curPacket, LinkedBlockingQueue<BlueNetPacket> packetSendQueue)
    {
        if ((curPacket.getPacketType() == BlueNetPacket.BN_PACKET_HTTP) ||
            (curPacket.getPacketType() == BlueNetPacket.BN_PACKET_HTTPS))
        {
            int tag = curPacket.getTag();
            int index = this.indexClass.getQueueIndexByTag(tag);
            LinkedBlockingQueue<Integer> closedTagQueue =
                this.closedTagQueueVec.get(index);
            int queueSize = packetSendQueue.size();

            if ((queueSize > 0) && (closedTagQueue.size() > 0))
            {
                int numPackets = packetSendQueue.drainTo(
                    sendPackets, BlueNetPacketAllocator.BN_MAX_SEND_QUEUE_LEN);
    
                Log.d(TAG, "Scanning Through " + numPackets + " Packets for Tag " + tag);
    
                // FIXME: While it shouldn't crash, this may not be accurate since other threads
                // can still be adding packets to the packetSendQueue that match this tag at
                // the same time as we're iterating over it :-(
                //
                // We guarantee with the synchronized block that nothing can be added to the
                // closedTagQueue structure though (so clearing at the end is safe)
                for(BlueNetPacket tmpPacket : this.sendPackets)
                {
                    for(Integer curTag : closedTagQueue)
                    {
                        if (tmpPacket.getTag() == curTag.intValue())
                        {
                            // NOTE: NEED TO CHECK curPacket as well
                            this.disposePacket(tmpPacket, false);
                        }
                        else if (this.finalSendPackets.contains(tmpPacket) == false)
                        {
                            // add back, careful not to add duplicates
                            this.finalSendPackets.add(tmpPacket);
                        }
                    }
                }
                closedTagQueue.clear();
                this.sendPackets.clear();
    
                packetSendQueue.addAll(finalSendPackets);
                this.finalSendPackets.clear();
            }
            Log.d(TAG, "Queue size reduced from " + queueSize + " to " + packetSendQueue.size());
        }
    }
    
    public void coalescePackets(BlueNetPacket curPacket,
                                LinkedBlockingQueue<BlueNetPacket> packetSendQueue)
        throws InterruptedException
    {
        if ((curPacket.getPacketType() == BlueNetPacket.BN_PACKET_HTTP) ||
            (curPacket.getPacketType() == BlueNetPacket.BN_PACKET_HTTPS))
        {
            // attempt to coalesce HTTP/HTTPS packet at head of queue, if tags match and they fit
            Log.d(TAG, "CONSIDERING CURPACKET: " + curPacket);

            int size = curPacket.getDataLength();
            do
            {
                BlueNetPacket nextPacket = packetSendQueue.peek();
                if (nextPacket == null)
                {
                    Log.d(TAG, "NO NEXT PACKET TO COALESCE");
                    break;
                }
                if (curPacket.getTag() == nextPacket.getTag())
                {
                    int nextLen = nextPacket.getDataLength();
                    if ((nextLen == -1) ||
                       ((size + nextLen) < BlueNetPacket.MAX_DATA_LEN))
                    {
                        nextPacket = packetSendQueue.take();
                        Log.d(TAG, "FOUND COALESCE CANDIDATE: " + nextPacket);
                        sendPackets.add(nextPacket);
                        if (nextLen == -1)
                        {
                            // break out of loop since the closePacket has to be
                            // the last one for this tag
                            break;
                        }
                        size += nextLen;
                    }
                    else
                    {
                        Log.d(TAG, "NEXT PACKET TO COALESCE IS TOO BIG; SKIPPING");
                        break;
                    }
                }
                else
                {
                    Log.d(TAG, "NEXT PACKET DOES NOT MATCH TAG");
                    break;
                }
            } while(size < BlueNetPacket.MAX_DATA_LEN);

            // sendPackets now contains all the ones we can fit in the curPacket
            if (sendPackets.size() > 0)
            {
                int index = 0;

                Log.d(TAG, "**** ABOUT TO COALESCE " + (sendPackets.size() + 1) + " PACKETS");
                if (curPacket.getDataLength() != -1)
                {
//                    Log.d(TAG, "[1] ARRAYCOPY CUR_INDEX=" + index + ", INCOMING LEN=" +
//                          curPacket.getDataLength());
                    System.arraycopy(
                        curPacket.getData(), 0, this.tmpData, index, curPacket.getDataLength());
                    index += curPacket.getDataLength();
                }

                boolean containsCloser = false;
                for(BlueNetPacket tmpPacket : sendPackets)
                {
                    if (tmpPacket.getDataLength() != -1)
                    {
//                        Log.d(TAG, "[2] ARRAYCOPY CUR_INDEX=" + index + ", INCOMING LEN=" +
//                              tmpPacket.getDataLength());
                        System.arraycopy(
                            tmpPacket.getData(), 0, this.tmpData, index, tmpPacket.getDataLength());
                        index += tmpPacket.getDataLength();
                    }
                    if (tmpPacket.shouldClose() == true)
                    {
                        containsCloser = true;
                    }
                    this.disposePacket(tmpPacket);
                }
                sendPackets.clear();

                // next, overwrite the packet data with our compacted data buffer
                if (curPacket.getDataLength() != -1)
                {
                    curPacket.setData(this.tmpData, index);
                }
                if (containsCloser == true)
                {
                    curPacket.setClose(true);
                }
            }
        }
    }

    private void closeSocketChannelObj(SocketChannel socketChannel) throws IOException
    {
        Socket socket = socketChannel.socket();
        if (socket != null)
        {
            socket.shutdownOutput();
            socket.shutdownInput();
            socket.close();
        }
        if (socketChannel.isOpen())
        {
            socketChannel.close();
        }

        socketChannel = null;
        socket = null;
    }

    private void teardownSocketChannelMappings(int tag)
    {
        if (this.httpsTagManager != null)
        {
            Integer httpsTag = this.httpsTagManager.getHTTPSTagFromHTTPTag(tag);
            if (httpsTag != null)
            {
                Log.d(TAG, "SENT TO TEARDOWN CHANNEL " + tag + ", BUT TEARING DOWN " + httpsTag + " INSTEAD");
                tag = httpsTag.intValue();
            }
        }
        SocketChannel socketChannel = (SocketChannel)this.channelManager.getObject(tag);
        if (socketChannel != null)
        {
            this.packetQueueManager.removeWriteList(tag);
            this.channelManager.removeObject(socketChannel);

            if (this.httpsTagManager != null)
            {
                this.httpsTagManager.removeEntry(tag);
            }

            try
            {
                this.closeSocketChannelObj(socketChannel);
            }
            catch(Exception e)
            {
                Log.e(TAG, "Failed to close socketChannel for tag " + tag + ": " + e);
            }     

            // since we just closed this tag, alert the bluetooth writer that
            // any queued tags for sending on this tag should be discarded
            int index = this.indexClass.getQueueIndexByTag(tag);
            LinkedBlockingQueue<Integer> closedTagQueue =
                this.closedTagQueueVec.get(index);
            synchronized(this)
            {
                closedTagQueue.offer(tag);
            }

            Log.d(TAG, "CHANNEL " + tag + " CLEANED UP NOW");
        }
        else
        {
            Log.d(TAG, "CANNOT FIND CHANNEL " + tag + " TO CLEAN UP :-(");            
        }
    }

    public void closeSocketChannel(int tag, SelectionKey key, SocketChannel socketChannel,
                                   boolean sendClosePacket, boolean teardownMappings)
    {
        if (sendClosePacket == true)
        {
            // Explicitly send a close packet over bluetooth network
            // so the server can tear down this socket connection
            BlueNetPacket closePacket = new BlueNetPacket(
                tag, BlueNetPacket.BN_PACKET_HTTP);
            closePacket.setClose(true);
            closePacket.setData(null, -1);

            // NOTE: we have to make sure the packet is queued while the mappings
            // for this tag are still intact.  otherwise, the client can receive
            // a close before all data is sent properly (in cases where tag throttling
            // is in action and data hasn't been queued yet).
            // To do this, we just send the packet here and the actual teardown
            // method is called by the BlueNetBluetoothWriter when it actually
            // sends the close packet
            this.preparePacketForSend(closePacket, key);
        }

        // this key cancellation is crucial here
        key.cancel();

        if (teardownMappings == true)
        {
            this.teardownSocketChannelMappings(tag);
        }
    }

    public void preparePacketForSend(BlueNetPacket curPacket, SelectionKey key)
    {
        int index = this.indexClass.getQueueIndexByTag(curPacket.getTag());

        if (this.httpsTagManager != null)
        {
            Integer httpTag = this.httpsTagManager.getHTTPTagFromHTTPSTag(
                curPacket.getTag());
            if (httpTag != null)
            {
                curPacket.setTag(httpTag.intValue());
                index = this.indexClass.getQueueIndexByTag(httpTag.intValue());
            }
        }

        LinkedBlockingQueue<BlueNetPacket> packetSendQueue =
            this.packetSendQueueVec.get(index);
        packetSendQueue.offer(curPacket);

//        Log.d(TAG, "PLACED PACKET ON QUEUE: " + curPacket + " (SEND QUEUE SIZE = " +
//            packetSendQueue.size() + ")");

        // Never attempt to throttle a connection that's about
        // to be terminated after the above packet is sent
        if (curPacket.shouldClose() == false)
        {
            int queueSize = packetSendQueue.size();
            if ((key != null) && (key.isValid()) &&
                (this.throttledKeyList.contains(key) == false) &&
                (queueSize > BlueNetPacketAllocator.BN_MAX_SEND_QUEUE_LEN))
            {
                // if the queue is too large, we need to throttle this tag
                // so that we can clear out the queue a bit before reading
                // more data
                // FIXME: THIS CAN STARVE INNOCENT TAGS!  NEED TO TRACK WHICH
                // TAGS ARE THE ACTUAL BAD GUYS AND THROTTLE THEM SPECIFICALLY?
                // PERHAPS DOESN'T MATTER IN PRACTICE ... NEED TO TEST
                
                // FIXME: TWO TODOS: 1) Track which tags are getting more than say 20 reads per second
                // and throttle them so that the read lengths are bigger on each read
                // 2) When the client closes a connection, try to clear the packetSendQueue of any
                // potential packets that need writing
                this.throttledKeyList.add(key);
    
                // stop listening for reads on this key (clear the read bit)
                key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
                Log.d(TAG, "THROTTLED READS ON TAG " +
                    curPacket.getTag() + " (Queue size = " + queueSize + ")");
            }
        }
    }

    public void handleEndOfStreamReached(int tag, SelectionKey key, SocketChannel socketChannel)
    {
        // first check if the tag has any packets on the write list
        if (this.packetQueueManager.getPacketWriteListSize(tag) > 0)
        {
            // continue listening to writes
            Log.d(TAG, "END OF STREAM FOUND, BUT CONTINUING TO LISTEN FOR WRITES");
            key.interestOps(SelectionKey.OP_WRITE);
            return;
        }
        else
        {
            // scan through packetSendQueue to see if any tags match for us
            int index = this.indexClass.getQueueIndexByTag(tag);
            LinkedBlockingQueue<BlueNetPacket> packetSendQueue =
                this.packetSendQueueVec.get(index);
            BlueNetPacket[] packets = packetSendQueue.toArray(this.tmpPacketArray);

            int numPackets = packets.length;
            for(int i = 0; i < numPackets; i++)
            {
                if (packets[i] == null)
                {
                    break;
                }
                else if (packets[i].getTag() == tag)
                {
                    // continue listening for writes
                    Log.d(TAG, "END OF STREAM FOUND, BUT CONTINUING TO LISTEN FOR WRITES");
                    key.interestOps(SelectionKey.OP_WRITE);
                    return;
                }
            }
        }
        // ONLY close the socket channel IF we have nothing left to write
        // AND if nothing is in the recv queue to write
        this.closeSocketChannel(tag, key, socketChannel, true, false);
    }

    // scans through all throttled tags and sees if any of their queues have reached
    // a reasonable size, in which case we remove them as throttled and set them to
    // be readable again
    public void updateThrottledKeys()
    {
        int len = this.throttledKeyList.size();
        if (len > 0)
        {
            int index = -1, curTag = -1;
            SelectionKey curKey = null;
            Vector<Integer> foundIndices = null;

            for(int i = 0; i < len; i++)
            {
                curKey = this.throttledKeyList.get(i);

                SocketChannel socketChannel = (SocketChannel)curKey.channel();
                curTag = this.channelManager.getTag(socketChannel);

                index = this.indexClass.getQueueIndexByTag(curTag);
                int queueSize = this.packetSendQueueVec.get(index).size();
                
                // This "- i" check makes sure that we don't free all when the queue
                // really only has a small number of slots left.  This helps balance ...
                if (queueSize < (BlueNetPacketAllocator.BN_MAX_SEND_QUEUE_LEN - i))
                {
                    if (foundIndices == null)
                    {
                        foundIndices = new Vector<Integer>();
                    }
                    foundIndices.add(new Integer(i));

                    // we have to check validity because it's very possible the network
                    // connection has been terminated, as we are still sending out the
                    // packets over bluetooth
                    if (curKey.isValid())
                    {
                        // start listening for reads again on this tag since the
                        // queue size has been reduced to a reasonable size
                        curKey.interestOps(SelectionKey.OP_READ);
                        Log.d(TAG, "Listening for READS " +
                           "(UNTHROTTLED) now on tag " + curTag + " (Queue size = " + queueSize + ")");
                    }
                }
                else
                {
                    break;
                }
            }
    
            if (foundIndices != null)
            {
                len = foundIndices.size();
                for(int i = 0; i < len; i++)
                {
                    this.throttledKeyList.remove(foundIndices.get(i) - i);
                }
            }
        }
    }

    public void shutdown()
    {
        try
        {
            this.selector.close();

            this.throttledKeyList.clear();
            this.packetQueueManager.removeAllWriteLists();
            this.channelManager.removeAllObjects();

            if (this.httpsTagManager != null)
            {
                this.httpsTagManager.removeAllEntries();
            }

            Log.d(TAG, "Shutdown Complete");
        }
        catch(Exception e)
        {
            Log.d(TAG, "Failed to shutdown: " + e);
        }
    }

    public void handleSelect(BlueNetNetworkStatistics netStats) throws Exception
    {
        Iterator<SelectionKey> iter = this.selector.selectedKeys().iterator();
        //Log.d(TAG, "THERE ARE " + this.selector.selectedKeys().size() + " SELECTION KEYS");
        while(iter.hasNext())
        {
            SelectionKey key = iter.next();
            iter.remove();

            if (!key.isValid())
            {
                continue;
            }

            // This will only be called on the server side
            if (key.isConnectable() && (this.httpsTagManager != null))
            {
                SocketChannel socketChannel = (SocketChannel)key.channel();
                try
                {
                    socketChannel.finishConnect();
                }
                catch(IOException ie)
                {
                    Log.d(TAG, "Failed to complete connection: " + ie);
                    this.closeSocketChannelObj(socketChannel);
                    continue;
                }

                Integer httpsTag = (Integer)key.attachment();
                if (httpsTag != null)
                {
                    // NOTE: both the positive and negative values need to map to something.
                    // while the positive value is never used, it needs to take up space in
                    // the channelManager as if something lived there so that tag mappings
                    // all work out properly.
                    // NOTE: Actually, instead, we pass true as last argument, which causes
                    // the channelManager to increment the internal index accordingly
                    // to keep tags coming out unique and consistent
                    this.channelManager.addEntry(httpsTag.intValue(), socketChannel, true);
                    Log.d(TAG, "Registered new HTTPS connection with HTTPS tag " + httpsTag);
                }
                else
                {
                    int httpTag = this.channelManager.addObject(socketChannel);                            
                    Log.d(TAG, "Registered new HTTP connection with HTTP tag " + httpTag);

                    // NOTE: We ONLY do this for HTTP connections since for HTTPS, we
                    // have already sent back the connection OK message, and when we
                    // received the next packet for actual writing, we will be woken
                    // up for that descriptor.
                    // If instead we always wait for writes, the write call will happen,
                    // no packet will be found/written, then when we listen for reads,
                    // it gets an end of stream before we ever receive the packet to write.
                    key.interestOps(SelectionKey.OP_WRITE);

                    netStats.incrementNetConnectionsConnected();
                }
            }
            // This will only be called on the Client side
            else if (key.isAcceptable() && (this.httpsTagManager == null))
            {
                ServerSocketChannel serverSocketChannel = (ServerSocketChannel)key.channel();
                SocketChannel socketChannel = serverSocketChannel.accept();
                socketChannel.configureBlocking(false);

                // Register the new SocketChannel with our Selector, indicating
                // we'd like to be notified when there's data waiting to be read
                socketChannel.register(this.selector, SelectionKey.OP_READ);

                int httpTag = this.channelManager.addObject(socketChannel);
                Log.d(TAG, "Accepted new HTTP browser connection with HTTP tag " + httpTag);

                netStats.incrementNetConnectionsAccepted();
            }
            else if (key.isReadable())
            {
                SocketChannel socketChannel = (SocketChannel)key.channel();
                int tag = this.channelManager.getTag(socketChannel);
                if (tag == ObjectManager.INVALID_TAG)
                {
                    continue;
                }
//                Log.d(TAG, "ABOUT TO DO A READ ON TAG " + tag);
                try
                {
                    // Clear out our read buffer so it's ready for new data
                    this.readBuffer.clear();

                    int nRead = socketChannel.read(this.readBuffer);
                    if (nRead == -1)
                    {
                        Log.d(TAG, "REACHED END OF READ STREAM FOR TAG " + tag);
                        this.handleEndOfStreamReached(tag, key, socketChannel);
                    }
                    else
                    {
                        netStats.addNetBytesRead(nRead);
//                        Log.d(TAG, "Read " + nRead + " bytes from tag " + tag);

                        BlueNetPacket curPacket = this.bnPacketAllocator.getNextPacketFromPool();
                        curPacket.setTag(tag);
                        curPacket.setPacketType(BlueNetPacket.BN_PACKET_HTTP);
                        curPacket.setData(this.readBuffer.array(), nRead);

                        this.preparePacketForSend(curPacket, key);
                    }
                }
                catch(IOException e)
                {
                    Log.d(TAG, "Failed to read from tag " + tag + ":" + e);

                    this.closeSocketChannel(tag, key, socketChannel, true, false);
                }
//                Log.d(TAG, "READ OP COMPLETE ON TAG " + tag);
            }
            else if (key.isWritable())
            {
                boolean channelClosed = false, placedPartial = false;
                SocketChannel socketChannel = (SocketChannel)key.channel();
                int tag = this.channelManager.getTag(socketChannel);
                if (tag == ObjectManager.INVALID_TAG)
                {
                    continue;
                }
//                Log.d(TAG, "ATTEMPTING TO DO A WRITE ON TAG " + tag);
                try
                {
                    ByteBuffer data = null;
                    boolean allDataWritten = false, anyDataWritten = false;
                    int nWritten = 0, totalWritten = 0, attempts = 0;
                    do
                    {
                        BlueNetPacket curPacket =
                            this.packetQueueManager.getNextPacketOnWriteList(tag);
                        if (curPacket == null)
                        {
                            break;
                        }

                        // be sure to reset totalWritten for each new packet in loop 
                        totalWritten = 0;

//                        Log.d(TAG, "GOT A PACKET TO WRITE ON TAG " + tag + " OF LENGTH " +
//                            curPacket.getDataLength());
                        if (curPacket.getDataLength() > 0)
                        {
                            data = ByteBuffer.wrap(curPacket.getData(), 0, curPacket.getDataLength());
                            do
                            {
                                nWritten = socketChannel.write(data);
                                totalWritten += nWritten;

                            } while((data.remaining() > 0) && (attempts++ < 3));

                            if (totalWritten > 0)
                            {
                                netStats.addNetBytesWritten(totalWritten);
                                anyDataWritten = true;
                            }

//                            Log.d(TAG, "WROTE " + totalWritten + " of: " + curPacket);

                            allDataWritten = (data.remaining() == 0);
                        }
                        else
                        {
                            allDataWritten = true;
                        }

                        // check if the socketChannel needs to be closed (ONLY if all
                        // data for this packet has already been written)
                        if ((curPacket.shouldClose() == true) && (allDataWritten == true))
                        {
                            Log.d(TAG, "SOCKET " + tag + " IS MARKED FOR CLOSING -- CLOSING NORMALLY");

                            this.closeSocketChannel(tag, key, socketChannel, false, true);
                            channelClosed = true;
                            break;
                        }

                        if ((anyDataWritten == true) && (allDataWritten == false))
                        {
                            // if all of the data couldn't be written, modify the packet data
                            // to contain the remaining data, and place it at the head of the
                            // pendingWriteList for this tag (and create that list where needed)
                            int newlen = (curPacket.getDataLength() - totalWritten);
                            curPacket.setData(curPacket.getData(), totalWritten, newlen);

                            Log.d(TAG, "Placed Partial Packet of size " + newlen + " on the WriteList");

                            this.packetQueueManager.addPacketToWriteListHead(tag, curPacket);

                            // instead of waking up the selector below, we just keep the
                            // interestOps including writes, rather than setting back to read
                            // (which we'd do if all the data was written normally)

                            placedPartial = true;
                            break;
                        }

                    } while(allDataWritten == true);

                    if ((channelClosed == false) && (anyDataWritten == false))
                    {
                        // if no data was written at all, then continue the loop
                        // meaning that we do NOT switch to listening to reads.
                        // if the channel was closed, we don't write anything, so
                        // we also check to not warn and ignore writes in that case
                        Log.d(TAG, "WARNING: NOTHING TO WRITE ON TAG " + tag + "; Ignoring writes");
                        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                    }
                    else if ((channelClosed == false) && (placedPartial == false))
                    {
                        // if we didn't close the channel and we didn't place a partial packet
                        // back on the writeList, switch to only listening for reads now
                        key.interestOps(SelectionKey.OP_READ);
//                        Log.d(TAG, "Listening for READS now on tag " + tag);
                    }
                }
                catch(IOException e)
                {
                    Log.d(TAG, "Failed to write to tag " + tag + ": " + e);

                    this.closeSocketChannel(tag, key, socketChannel, true, false);
                }
//                Log.d(TAG, "WRITE OP COMPLETE ON TAG " + tag);
            }
        }

        // when all other I/O operations are complete, check here if we were woken
        // up to the queue size having come down beneath the desired max length
        this.updateThrottledKeys();
    }
}
