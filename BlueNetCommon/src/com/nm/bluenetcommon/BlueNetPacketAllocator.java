package com.nm.bluenetcommon;

import java.util.concurrent.LinkedBlockingQueue;

public class BlueNetPacketAllocator
{
    private static byte[] nullData = new byte[BlueNetPacket.MAX_DATA_LEN];

    public final static int BN_MAX_SEND_QUEUE_LEN = 20;

    private int BN_MAX_POOL_LIST_LEN = (BN_MAX_SEND_QUEUE_LEN + 1);

    private int maxNumQueues = 0;
    private LinkedBlockingQueue<BlueNetPacket> packetPool = null;

    public BlueNetPacketAllocator(int maxNumQueues)
    {
        this.maxNumQueues = maxNumQueues;
        this.packetPool = new LinkedBlockingQueue<BlueNetPacket>();
        for(int i = 0; i < BlueNetPacket.MAX_DATA_LEN; i++)
        {
            nullData[i] = '\0';
        }
        this.initPacketPool();
    }

    private void initPacketPool()
    {
        int totalPackets = (this.BN_MAX_POOL_LIST_LEN * this.maxNumQueues);
        for(int i = 0; i < totalPackets; i++)
        {
            BlueNetPacket curPacket = new BlueNetPacket();
            curPacket.clear(nullData, BlueNetPacket.MAX_DATA_LEN);
            curPacket.setPooled(true);
            this.packetPool.offer(curPacket);
        }
    }

    public int getPacketPoolSize()
    {
        return this.packetPool.size();
    }

    // cannot be synchronized, or else a simultaneous call into this
    // and returnPacketToPool will cause a deadlock.
    // linked blocking queue structure is thread-safe anyway
    public BlueNetPacket getNextPacketFromPool() throws InterruptedException
    {
        return this.packetPool.take();
    }

    public void returnPacketToPool(BlueNetPacket curPacket)
    {
        if (!this.packetPool.contains(curPacket))
        {
            curPacket.clear(nullData, BlueNetPacket.MAX_DATA_LEN);
            curPacket.setPooled(true);
            this.packetPool.offer(curPacket);
        }
    }
}
