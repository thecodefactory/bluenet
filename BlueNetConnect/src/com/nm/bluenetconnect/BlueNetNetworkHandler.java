package com.nm.bluenetconnect;

import com.nm.bluenetcommon.BlueNetNetworkCommon;
import com.nm.bluenetcommon.BlueNetPacket;
import com.nm.bluenetcommon.BlueNetPacketAllocator;
import com.nm.bluenetcommon.ObjectManager;
import com.nm.bluenetcommon.PacketQueueManager;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;

public class BlueNetNetworkHandler extends Thread
{
    BlueNetLogger Log = new BlueNetLogger();
    private static final String TAG = "BlueNetNetworkHandler";

    private int port = -1;
    private boolean running = false;
    private Selector selector = null;
    private BlueNetConnect bnConnect = null;
    private NetworkStatistics netStats = null;
    private ObjectManager channelManager = null;
    private ServerSocketChannel serverChannel = null;
    private PacketQueueManager packetQueueManager = null;
    private BlueNetPacketAllocator bnPacketAllocator = null;
    private Vector<LinkedBlockingQueue<BlueNetPacket>> packetSendQueueVec = null;
    private Vector<LinkedBlockingQueue<Integer>> closedTagQueueVec = null;
    private BlueNetNetworkCommon bnNetworkCommon = null;

    public BlueNetNetworkHandler(
        int port, BlueNetConnect bnConnect, ObjectManager channelManager,
        Selector selector, PacketQueueManager packetQueueManager,
        Vector<LinkedBlockingQueue<BlueNetPacket>> packetSendQueueVec,
        BlueNetPacketAllocator bnPacketAllocator,
        Vector<LinkedBlockingQueue<Integer>> closedTagQueueVec,
        NetworkStatistics netStats)
    {
        this.port = port;
        this.bnConnect = bnConnect;
        this.channelManager = channelManager;
        this.selector = selector;
        this.packetQueueManager = packetQueueManager;
        this.packetSendQueueVec = packetSendQueueVec;
        this.bnPacketAllocator = bnPacketAllocator;
        this.closedTagQueueVec = closedTagQueueVec;
        this.netStats = netStats;
    }

    public void updateQueueSize(int queueSize)
    {
        this.bnNetworkCommon.updateQueueSize(queueSize);
    }

    public void disposePacket(BlueNetPacket curPacket)
    {
        this.bnNetworkCommon.disposePacket(curPacket);        
    }

    public void clearClosedTagsIfAny(
        BlueNetPacket curPacket, LinkedBlockingQueue<BlueNetPacket> packetSendQueue)
    {
        this.bnNetworkCommon.clearClosedTagsIfAny(curPacket, packetSendQueue);
    }

    public void coalescePackets(BlueNetPacket curPacket,
                                LinkedBlockingQueue<BlueNetPacket> packetSendQueue)
        throws InterruptedException
    {
        this.bnNetworkCommon.coalescePackets(curPacket, packetSendQueue);
    }

    public void shutdown()
    {
        this.running = false;
        this.bnNetworkCommon.shutdown();
    }

    public void run()
    {
        this.running = true;
        try
        {
            this.bnNetworkCommon = new BlueNetNetworkCommon(
                this.Log, this.bnConnect, this.channelManager, this.selector, null,
                this.packetQueueManager, this.packetSendQueueVec,
                this.closedTagQueueVec, this.bnPacketAllocator);

            InetSocketAddress isa = new InetSocketAddress(this.port);

            this.serverChannel = ServerSocketChannel.open();
            this.serverChannel.configureBlocking(false);
            this.serverChannel.socket().bind(isa);
            this.serverChannel.register(this.selector, SelectionKey.OP_ACCEPT);

            Log.i(TAG, "Initialize complete -- Listening for data on port " + port);

            while(this.running == true)
            {
                // wait for socket accept/read events or a wake-up
                // call when packets need to be written
                this.selector.select();

                this.bnNetworkCommon.handleSelect(this.netStats);
            }
        }
        catch(Exception e)
        {
            Log.e(TAG, "Fatal Error: " + e);
            e.printStackTrace();
        }
        finally
        {
            try { this.serverChannel.close(); } catch(Exception e) {}
        }
        Log.e(TAG, "***** BlueNetNetworkHandler Thread terminating *****");
    }
}
