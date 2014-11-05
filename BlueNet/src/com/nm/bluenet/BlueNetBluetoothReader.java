package com.nm.bluenet;

import com.nm.bluenetcommon.BlueNetPacket;
import com.nm.bluenetcommon.DataUtils;
import com.nm.bluenetcommon.HTTPSTagManager;
import com.nm.bluenetcommon.ObjectManager;
import com.nm.bluenetcommon.PacketQueueManager;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;

import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

public class BlueNetBluetoothReader extends Thread
{
    private BlueNetLogger Log = new BlueNetLogger();
	private static final String TAG = "BlueNetBluetoothReader";

	private int id = -1;
	private boolean running = false;
    private Selector selector = null;
    private Lock connectLock = null;
    private NetworkStatistics netStats = null;
	private BluetoothSocket mainSocket = null;
	private BluetoothServerSocket socket = null;
	private ObjectManager channelManager = null;
    private HTTPSTagManager httpsTagManager = null;
	private PacketQueueManager packetQueueManager = null;
    private Vector<Integer> pendingConnectionList = null;
	private LinkedBlockingQueue<BlueNetPacket> packetSendQueue = null;
    private LinkedBlockingQueue<BlueNetPacket> dnsPacketQueue = null;

	public BlueNetBluetoothReader(
	    int id, BluetoothServerSocket socket, ObjectManager channelManager,
	    Selector selector, Lock connectLock,
	    HTTPSTagManager httpsTagManager, PacketQueueManager packetQueueManager,
        LinkedBlockingQueue<BlueNetPacket> packetSendQueue,
        LinkedBlockingQueue<BlueNetPacket> dnsPacketQueue,
        NetworkStatistics netStats)
	{
		this.running = true;
		this.id = id;
		this.socket = socket;
		this.channelManager = channelManager;
        this.selector = selector;
		this.connectLock = connectLock;
		this.httpsTagManager = httpsTagManager;
        this.packetQueueManager = packetQueueManager;
        this.packetSendQueue = packetSendQueue;
		this.dnsPacketQueue = dnsPacketQueue;
		this.netStats = netStats;
		this.pendingConnectionList = new Vector<Integer>();
	}

	public BluetoothSocket getMainSocket()
	{
		return this.mainSocket;
	}


    // we special case the CONNECT, so we don't support it in this method
    private int getLengthOfSupportedCommand(byte[] srcBuffer)
    {
        if (DataUtils.isGetRequest(srcBuffer)) { return 4; }
        if (DataUtils.isPutRequest(srcBuffer)) { return 4; }
        if (DataUtils.isPostRequest(srcBuffer)) { return 5; }
        if (DataUtils.isHeadRequest(srcBuffer)) { return 5; }
        if (DataUtils.isTraceRequest(srcBuffer)) { return 6; }
        if (DataUtils.isPatchRequest(srcBuffer)) { return 6; }
        if (DataUtils.isDeleteRequest(srcBuffer)) { return 7; }
        if (DataUtils.isOptionsRequest(srcBuffer)) { return 8; }
        return -1;
    }

    private SocketChannel initiateHTTPSocketChannel(BlueNetPacket curPacket) throws Exception
    {
        SocketChannel ret = null;
        byte[] srcBuffer = curPacket.getData();
        int srcLen = curPacket.getDataLength();
        if (srcLen > 9)
        {
            int commandLen = getLengthOfSupportedCommand(srcBuffer);
            if (commandLen != -1)
            {
                int pos = -1;
                // find the position of the next space after the first
                for(int i = commandLen; i < srcLen; i++)
                {
                    if (srcBuffer[i] == 0x20)
                    {
                        pos = i;
                        break;
                    }
                }
                if (pos != -1)
                {
                    byte[] tmpArray = new byte[pos - commandLen];
                    for(int i = commandLen, j = 0; i < pos; i++, j++)
                    {
                        tmpArray[j] = srcBuffer[i];
                    }
                    String site = new String(tmpArray);
                    URL siteURL = new URL(site);
                    if (siteURL.getPort() == -1)
                    {
                        siteURL = new URL(siteURL.getProtocol(), siteURL.getHost(),
                                80, siteURL.getFile());
                    }
                    Log.d(TAG, "Parsed Site " + siteURL.getHost() +
                        " and Port " + siteURL.getPort());

                    SocketChannel tmp = SocketChannel.open();
                    tmp.configureBlocking(false);
                    tmp.connect(new InetSocketAddress(siteURL.getHost(), siteURL.getPort()));

                    try
                    {
                        // hold the connect lock, but wake up the selection thread
                        // (connect cannot complete otherwise)
                        this.connectLock.lock();
                        this.selector.wakeup();

                        tmp.register(this.selector, SelectionKey.OP_CONNECT);
                    }
                    catch(Exception e2)
                    {
                        tmp.close();
                        Log.e(TAG, "Exception registering for Connect: " + e2);
                    }
                    finally
                    {
                        this.connectLock.unlock();                        
                    }

                    Log.d(TAG, "New Packet added to writeList for tag " + curPacket.getTag());
                    // store the payload data so that when the connect is complete,
                    // it'll be available for sending in the network handler
                    this.packetQueueManager.addPacketToWriteListTail(
                        curPacket.getTag(), curPacket);

                    // wake up the selector so it can finish connect operation
                    this.selector.wakeup();
                    Log.d(TAG, "Selector called to wakeup for tag " + curPacket.getTag());

                    ret = tmp;
                }
                else
                {
                    Log.e(TAG, "Failed to find site.");
                }
            }
            else if (DataUtils.isConnectRequest(srcBuffer) == false)
            {
                Log.e(TAG, "[1] FIXME: NOT SURE WHAT TO DO WITH THIS DATA:\n" + new String(srcBuffer));
            }
        }
        else
        {
            Log.e(TAG, "[2] FIXME: NOT SURE WHAT TO DO WITH THIS DATA:\n" + new String(srcBuffer));
        }
        return ret;
    }

    // returns the new HTTPS Tag
    private int initiateHTTPSSocketChannel(BlueNetPacket curPacket) throws Exception
    {
        // first create a tunnel socket to the host referenced in the request
        String tmpStr = new String(curPacket.getData());
        String[] pieces = tmpStr.split(" ", 3);
        String site = "https://" + pieces[1];
        URL siteURL = new URL(site);
        if (siteURL.getPort() == -1)
        {
            siteURL = new URL(siteURL.getProtocol(), siteURL.getHost(), 443, siteURL.getFile());
        }

        Log.d(TAG, "Creating HTTPS Socket to " + siteURL.getHost() +
            " AND PORT " + siteURL.getPort());

        SocketChannel httpsChannel = SocketChannel.open();
        httpsChannel.configureBlocking(false);
        httpsChannel.connect(new InetSocketAddress(siteURL.getHost(), siteURL.getPort()));

        int newHTTPSTag = -curPacket.getTag();
        try
        {
            // hold the connect lock, but wake up the selection thread
            // (connect cannot complete otherwise)
            this.connectLock.lock();
            this.selector.wakeup();

            httpsChannel.register(
                this.selector, SelectionKey.OP_CONNECT, new Integer(newHTTPSTag));
            this.connectLock.unlock();
        }
        catch(Exception e2)
        {
            httpsChannel.close();
            Log.e(TAG, "Exception registering for Connect: " + e2);
        }

        // form 200 OK response back to be written back to bluetooth client
        String btResponse = "HTTP/1.0 200 Connection established\r\n" +
            "Proxy-agent: BlueNet\r\n\r\n";

        // route response back to original socket connection
        BlueNetPacket responsePacket = new BlueNetPacket(
            curPacket.getTag(), BlueNetPacket.BN_PACKET_HTTPS,
            btResponse.getBytes(), btResponse.length());

        // register this new tag with the HTTPS socket mapper
        this.httpsTagManager.addEntry(curPacket.getTag(), newHTTPSTag);

        Log.d(TAG, "ADDED NEW HTTPS Tag " + curPacket.getTag() + " (HTTP) ==> " + newHTTPSTag +
            " (HTTPS) to httpsTagManager");

        // NEED TO SEND THIS RESPONSE OVER BLUETOOTH DIRECTLY!
        this.packetSendQueue.offer(responsePacket);

        return newHTTPSTag;
    }

    private boolean isAPendingConnection(int tag)
    {
        boolean ret = false;
        for(Integer curTag : this.pendingConnectionList)
        {
            if (curTag.intValue() == tag)
            {
                ret = true;
                break;
            }
        }
        return ret;
    }

    private void clearPendingConnection(int tag)
    {
        int foundIndex = -1;
        int len = this.pendingConnectionList.size();
        for(int i = 0; i < len; i++)
        {
            if (this.pendingConnectionList.get(i).intValue() == tag)
            {
                foundIndex = i;
                break;
            }
        }
        if (foundIndex > -1)
        {
            this.pendingConnectionList.remove(foundIndex);
        }
    }

    private void teardownSocketChannelMappings(SocketChannel socketChannel, int tag) throws Exception
    {
        this.packetQueueManager.removeWriteList(tag);
        this.channelManager.removeObject(socketChannel);

        if (this.httpsTagManager != null)
        {
            this.httpsTagManager.removeEntry(tag);
        }
        SelectionKey key = socketChannel.keyFor(this.selector);
        if (key != null)
        {
            key.cancel();
        }
        socketChannel.close();
        Log.d(TAG, "BOGUS(?) CHANNEL " + tag + " CLEANED UP NOW");
    }

    // either places the packet on an existing writeList and wakes up the network handling
    // selector, or starts a new connection (and adds the data to a new writeList)
    private void handleWebPacket(BlueNetPacket curPacket) throws Exception
    {
        int tag = curPacket.getTag();
        Integer httpsTag = this.httpsTagManager.getHTTPSTagFromHTTPTag(tag);
        if (httpsTag != null)
        {
            Log.d(TAG, "GOT PACKET TAG " + tag + ", BUT USING KNOWN HTTPS TAG " + httpsTag);
            tag = httpsTag.intValue();
        }
        SocketChannel channel = (SocketChannel)this.channelManager.getObject(tag);
        if (channel != null)
        {
            SelectionKey key = channel.keyFor(this.selector);
            if (key != null)
            {
                this.clearPendingConnection(tag);

                // NOTE: since tag can be overwritten via HTTPS, manually specify tag
                this.packetQueueManager.addPacketToWriteListTail(tag, curPacket);
                Log.d(TAG, "[" + this.id + "] Added packet to write list for tag " + tag);
    
                // wake up the selector since a write is ready on this socket channel
                key.interestOps(SelectionKey.OP_WRITE);
                this.selector.wakeup();
            }
            else if (curPacket.shouldClose())
            {
                this.teardownSocketChannelMappings(channel, tag);
            }
            else
            {
                Log.d(TAG, "[" + this.id + "] Ignoring received packet for tag " +
                    tag + " (Closing Key)");

                this.teardownSocketChannelMappings(channel, tag);
            }
        }
        else
        {
            if (this.isAPendingConnection(tag) == false)
            {
                try
                {
                    // check if we're starting an HTTPS Connection (which requires
                    // some special case handling at the beginning)
                    if ((curPacket.shouldClose() == false) &&
                        (DataUtils.isConnectRequest(curPacket.getData()) == true))
                    {
                        Log.d(TAG, "Initiating new HTTPS SocketChannel for Tag " + tag);
    
                        // establish the HTTPS connection and setup structure for connection lifetime
                        tag = this.initiateHTTPSSocketChannel(curPacket);
                    }
                    else if (curPacket.shouldClose() && (curPacket.getDataLength() < 0))
                    {
                        Log.d(TAG, "Ignoring unnecessary ClosePacket sent for Tag " + tag);
                        return;
                    }
                    else
                    {
                        Log.d(TAG, "Initiating new HTTP SocketChannel for Tag " + tag);

                        // starts a connection to the site contained in the packet
                        // and registers it with the selector so it can be handled
                        // later. it also adds the packet payload to the packetmanager
                        this.initiateHTTPSocketChannel(curPacket);
                    }
                    this.pendingConnectionList.add(new Integer(tag));
                    Log.d(TAG, "Added pending connection marker for tag " + tag);
                }
                catch(Exception e)
                {
                    // FIXME: NEED TO FIX THIS IN REGARDS TO THROTTLE METHOD!!!
                    // alert the client that this connection is dead
                    curPacket.setClose(true);
                    curPacket.setData(null, -1);
    
                    this.packetSendQueue.offer(curPacket);
    
                    Log.e(TAG, "EXCEPTION: " + e);
                    Log.e(TAG, "Failed to connect to site.  Ignoring packet data for tag " +
                        curPacket.getTag());
                }
            }
            else
            {
                Log.d(TAG, "CONNECTION NOT ESTABLISHED YET -- Adding packet to writeList");
                // add this packet to the writeList for this tag; no need to wake up
                // selector since we have to wait until the connection has completed
                // before writes are considered
                // NOTE: since tag can be overwritten via HTTPS, manually specify tag
                this.packetQueueManager.addPacketToWriteListTail(tag, curPacket);
                Log.d(TAG, "[" + this.id + "] Added packet to write list for tag " + tag);
            }
        }
    }

    public void shutdown()
    {
        this.running = false;
        try { this.socket.close(); } catch(Exception e) {}
    }

    public void run()
	{
	    Log.d(TAG, "[" + this.id + "] ***** BlueNetBluetoothReader thread started *****");
		try
		{
			Log.d(TAG, "[" + this.id + "] About to listen for BT connection ...");
			this.mainSocket = this.socket.accept();
			Log.d(TAG, "[" + this.id + "] BT connection accepted!");

			InputStream is = mainSocket.getInputStream();
			InputStream buffer = new BufferedInputStream(is);
			ObjectInputStream dataInput = new ObjectInputStream(buffer);

			Log.d(TAG, "[" + this.id + "] About to start main loop ...");
			while(this.running == true)
			{
			    this.netStats.setBluetoothReadWaiting();
			    BlueNetPacket curPacket = (BlueNetPacket)dataInput.readUnshared();
			    this.netStats.setBluetoothReadActive();

				Log.d(TAG, "[" + this.id + "] Got " + curPacket);

				this.netStats.addBluetoothBytesRead(curPacket.getDataLength());

				// and place on the appropriate queue
				switch(curPacket.getPacketType())
				{
					case BlueNetPacket.BN_PACKET_HTTP:
					case BlueNetPacket.BN_PACKET_HTTPS:
					{
					    handleWebPacket(curPacket);
						break;
					}
					case BlueNetPacket.BN_PACKET_DNS:
					{
						this.dnsPacketQueue.offer(curPacket);
						break;
					}
					case BlueNetPacket.BN_PACKET_PING:
					{
                        // FIXME: update netstats
					    this.netStats.incrementNumPingsReceived();
					    curPacket.setPacketType(BlueNetPacket.BN_PACKET_PONG);
					    this.packetSendQueue.offer(curPacket);
					    break;
					}
                    case BlueNetPacket.BN_PACKET_PONG:
                    {
                        this.netStats.incrementNumPongsReceived();
                        long curTime = System.currentTimeMillis();
                        long pingSendTime = curPacket.getPingSendTime();
                        long pingTime = curTime - pingSendTime;
                        Log.d(TAG, "PONG RECEIVED.  Ping Time was " + pingTime);
                        this.netStats.setLastPingTime(pingTime);
                        // update netstats
                        break;
                    }
					default:
					{
						Log.e(TAG, "FIXME: SHOULD NEVER GET UNKNOWN PACKET TYPE");
						break;
					}
				}
			}
			Log.d(TAG, "[" + this.id + "] Exited from main loop");
		}
		catch(IOException ie)
		{
		    // these errors are okay to ignore (normal on shutdown)
		    Log.d(TAG, "[" + this.id + "] Shutting down BlueNetBluetoothReader");
//            Log.e(TAG, "[" + this.id + "] Socket Error: " + ie + " -- SHUTTING DOWN");
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ie.printStackTrace(pw);
            Log.e(TAG, sw.toString());
            // FIXME: CALL BACK INTO SPAWN TO SHOW A WARNING AND CAUSE PROPER SHUTDOWN
            System.exit(-1);
		}
		catch(Exception e)
		{
			Log.e(TAG, "[" + this.id + "] Fatal Error: " + e);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            Log.e(TAG, sw.toString());
            // FIXME: CALL BACK INTO SPAWN TO SHOW A WARNING AND CAUSE PROPER SHUTDOWN
            System.exit(-1);
		}
		finally
		{
			try { this.socket.close(); } catch(Exception e) {}
		}
		Log.d(TAG, "[" + this.id + "] ***** BlueNetBluetoothReader Thread terminating *****");
	}
}
