package com.nm.bluenetcommon;

import java.util.concurrent.LinkedBlockingQueue;

public class BlueNetPingSender extends Thread
{
    private static final String TAG = "BlueNetPingSender";

    private int id = -1;
    private boolean running = false;
    private BlueNetLog Log = null;
    private BlueNetNetworkStatistics netStats = null;
    private BlueNetPacketAllocator bnPacketAllocator = null;
    private LinkedBlockingQueue<BlueNetPacket> packetSendQueue = null;

    // need queue to send outgoing pings on and netstats object
    public BlueNetPingSender(int id, BlueNetLog Log,
                             LinkedBlockingQueue<BlueNetPacket> packetSendQueue,
                             BlueNetPacketAllocator bnPacketAllocator,
                             BlueNetNetworkStatistics netStats)
    {
        this.id = id;
        this.Log = Log;
        this.packetSendQueue = packetSendQueue;
        this.bnPacketAllocator = bnPacketAllocator;
        this.netStats = netStats;
    }

    public void shutdown()
    {
        this.running = false;
    }

    public void run()
    {
        try
        {
            Log.d(TAG, "[" + this.id + "] ***** BlueNetPingSender thread started *****");

            this.running = true;

            int queueSize = 0;
            final int halfCapacity = (BlueNetPacketAllocator.BN_MAX_SEND_QUEUE_LEN / 2);

            while(this.running == true)
            {
                queueSize = this.packetSendQueue.size();

                // don't bother with sending pings when the queue size is at more than half capacity
                // or we have not received a pong response from our last queued/sent ping
                if ((queueSize < halfCapacity) &&
                    (this.netStats.getNumPingsSent() == this.netStats.getNumPongsReceived()))
                {
                    BlueNetPacket curPacket = this.bnPacketAllocator.getNextPacketFromPool();
                    curPacket.setPacketType(BlueNetPacket.BN_PACKET_PING);
                    curPacket.setTag(Integer.MAX_VALUE - this.id);
                    // NOTE: packet PingTime is set just before the packet is sent

                    this.packetSendQueue.offer(curPacket);
    
                    Log.d(TAG, "[" + this.id + "] Ping Packet Offered");
                }
                else
                {
                    Log.d(TAG, "Decided to NOT send a ping at this time");
                }
                Thread.sleep(10000);
            }
        }
        catch(Exception e)
        {
            Log.e(TAG, "Failed: " + e);
        }
        Log.d(TAG, "[" + this.id + "] ***** BlueNetPingSender thread terminating *****");
    }
}
