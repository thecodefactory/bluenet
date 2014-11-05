package com.nm.bluenet;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.channels.Selector;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;

import com.nm.bluenetcommon.BlueNetNetworkCommon;
import com.nm.bluenetcommon.BlueNetPacket;
import com.nm.bluenetcommon.BlueNetPacketAllocator;
import com.nm.bluenetcommon.HTTPSTagManager;
import com.nm.bluenetcommon.ObjectManager;
import com.nm.bluenetcommon.PacketQueueManager;

public class BlueNetNetworkHandler extends Thread
{
    private BlueNetLogger Log = new BlueNetLogger();
    private static final String TAG = "BlueNetNetworkHandler";

    private boolean running = false;
    private boolean haveLock = false;

    private Spawn spawn = null;
    private Lock connectLock = null;
    private Selector selector = null;
    private NetworkStatistics netStats = null;
    private ObjectManager channelManager = null;
    private HTTPSTagManager httpsTagManager = null;
    private PacketQueueManager packetQueueManager = null;
    private BlueNetPacketAllocator bnPacketAllocator = null;
    private Vector<LinkedBlockingQueue<BlueNetPacket>> packetSendQueueVec = null;
    private Vector<LinkedBlockingQueue<Integer>> closedTagQueueVec = null;
    private BlueNetNetworkCommon bnNetworkCommon = null;

    public BlueNetNetworkHandler(
        Spawn spawn, ObjectManager channelManager, Selector selector,
        Lock connectLock, HTTPSTagManager httpsTagManager,
        PacketQueueManager packetQueueManager,
        Vector<LinkedBlockingQueue<BlueNetPacket>> packetSendQueueVec,
        BlueNetPacketAllocator bnPacketAllocator,
        Vector<LinkedBlockingQueue<Integer>> closedTagQueueVec,
        NetworkStatistics netStats)
    {
        this.spawn = spawn;
        this.channelManager = channelManager;
        this.selector = selector;
        this.connectLock = connectLock;
        this.httpsTagManager = httpsTagManager;
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

    public void disposePacket(BlueNetPacket curPacket, boolean shouldClose)
    {
        this.bnNetworkCommon.disposePacket(curPacket, shouldClose);
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
                this.Log, this.spawn, this.channelManager, this.selector, this.httpsTagManager,
                this.packetQueueManager, this.packetSendQueueVec,
                this.closedTagQueueVec, this.bnPacketAllocator);

            while(this.running == true)
            {
                // wait for socket accept/read events or a wake-up
                // call when packets need to be written
                //Log.d(TAG, "About to sleep on selector ...");
                if (this.haveLock == true)
                {
                    this.connectLock.unlock();
                    this.haveLock = false;
                }

                this.selector.select();
                if (!this.selector.isOpen())
                {
                    Log.d(TAG, "***** Selector closed -- shutting down main loop *****");
                    // FIXME: HANDLE BETTER
                    System.exit(-1);
                    break;
                }

                this.connectLock.lock();
                this.haveLock = true;

                //Log.d(TAG, "Ok, awake now and have lock");

                this.bnNetworkCommon.handleSelect(this.netStats);
            }
        }
        catch(Exception e)
        {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            Log.e(TAG, "BlueNetNetworkHandler: Fatal Error: " + sw.toString());
            // FIXME: CALL BACK INTO SPAWN TO SHOW A WARNING AND CAUSE PROPER SHUTDOWN
            System.exit(-1);
        }
    }
}
