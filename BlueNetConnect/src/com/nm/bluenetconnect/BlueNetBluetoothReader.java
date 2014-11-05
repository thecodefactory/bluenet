package com.nm.bluenetconnect;

import com.nm.bluenetcommon.BlueNetPacket;
import com.nm.bluenetcommon.ObjectManager;
import com.nm.bluenetcommon.PacketQueueManager;

import java.io.BufferedInputStream;
import java.io.ObjectInputStream;
import java.io.InputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import javax.microedition.io.StreamConnection;

public class BlueNetBluetoothReader extends Thread
{
    BlueNetLogger Log = new BlueNetLogger();
    private static final String TAG = "BlueNetBluetoothReader";

    private int id = -1;
    private boolean running = false;
    private StreamConnection sc = null;
    private BlueNetBluetoothWriter btWriter = null;
    private Selector selector = null;
    private ObjectManager channelManager = null;
    private PacketQueueManager packetQueueManager = null;
    private LinkedBlockingQueue<BlueNetPacket> dnsSendQueue = null;

    public BlueNetBluetoothReader(
        int id, BlueNetBluetoothWriter btWriter, ObjectManager channelManager,
        Selector selector, PacketQueueManager packetQueueManager,
        LinkedBlockingQueue<BlueNetPacket> dnsSendQueue)
    {
        this.running = true;
        this.id = id;
        this.btWriter = btWriter;
        this.channelManager = channelManager;
        this.selector = selector;
        this.packetQueueManager = packetQueueManager;
        this.dnsSendQueue = dnsSendQueue;
    }

    public void run()
    {
        try
        {
            Log.i(TAG, "[" + this.id + "] ***** BlueNetBluetoothReader thread started *****");
            do
            {
                this.sc = this.btWriter.getMainStreamConnection();
                if (this.sc == null)
                {
                    try { Thread.sleep(100); } catch(Exception e) {}
                }
            } while(this.sc == null);

            Log.i(TAG, "[" + this.id + "] Connection retrieved");

            InputStream is = this.sc.openInputStream();
            BufferedInputStream bufferedIS = new BufferedInputStream(is);
            ObjectInputStream dataInput = new ObjectInputStream(bufferedIS);

            while(this.running == true)
            {
                BlueNetPacket curPacket = (BlueNetPacket)dataInput.readUnshared();

                Log.i(TAG, "[" + this.id + "] GOT: " + curPacket);

                // place on the appropriate queue
                switch(curPacket.getPacketType())
                {
                    case BlueNetPacket.BN_PACKET_HTTP:
                    case BlueNetPacket.BN_PACKET_HTTPS:
                    {
                        int tag = curPacket.getTag();
                        SocketChannel channel = (SocketChannel)this.channelManager.getObject(tag);
                        if (channel != null)
                        {
                            SelectionKey key = channel.keyFor(this.selector);
                            if (key != null)
                            {
                                this.packetQueueManager.addPacketToWriteListTail(tag, curPacket);
                                Log.i(TAG, "[" + this.id + "] Added packet to write list for tag " + tag);

                                try
                                {
                                    // wake up the selector since a write is ready on this socket channel
                                    key.interestOps(SelectionKey.OP_WRITE);
                                    this.selector.wakeup();
                                }
                                catch(Exception e2)
                                {
                                    if (e2 instanceof java.nio.channels.CancelledKeyException)
                                    {
                                        Log.i(TAG, "Socket key was cancelled -- nothing left to do");
                                        continue;
                                    }
                                    Log.e(TAG, "Unexpected fatal error: " + e2);
                                }
                            }
                            else
                            {
                                Log.i(TAG, "[" + this.id + "] Ignoring received packet for tag " + tag + " (Closing Key)");

                                this.channelManager.removeObject(tag);
                                channel.close();
                            }
                        }
                        else
                        {
                            Log.i(TAG, "[" + this.id + "] Ignoring received packet for tag " + tag + " (No Channel)");
                        }
                        break;
                    }
                    case BlueNetPacket.BN_PACKET_DNS:
                    {
                        this.dnsSendQueue.offer(curPacket);
                        break;
                    }
                    case BlueNetPacket.BN_PACKET_PING:
                    {
                        // FIXME: update netstats
                        //this.netStats.incrementNumPingsReceived();
                        curPacket.setPacketType(BlueNetPacket.BN_PACKET_PONG);
                        this.btWriter.sendPacket(curPacket);
                        break;
                    }
                    case BlueNetPacket.BN_PACKET_PONG:
                    {
                        //this.netStats.incrementNumPongsReceived();
                        long curTime = System.currentTimeMillis();
                        long pingSendTime = curPacket.getPingSendTime();
                        long pingTime = curTime - pingSendTime;
                        Log.d(TAG, "PONG RECEIVED.  Ping Time was " + pingTime);
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
        }
        catch(Exception e)
        {
            if (e instanceof java.io.EOFException)
            {
                Log.i(TAG, "[" + this.id + "] detected server app shutdown");
            }
            else
            {
                Log.e(TAG, "[" + this.id + "] Fatal unexpected error: " + e);
                e.printStackTrace();
            }
        }
        finally
        {
            try { this.sc.close(); } catch(Exception e) {}
            // FIXME: Call into BlueNetConnect to shut everything down?
            System.exit(0);
        }
        Log.e(TAG, "[" + this.id + "] ***** BlueNetBluetoothReader terminating *****");
    }
}
