package com.nm.bluenetconnect;

import com.nm.bluenetcommon.BlueNetPacket;

import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.LinkedBlockingQueue;
import javax.microedition.io.StreamConnection;

public class BlueNetBluetoothWriter extends Thread
{
    BlueNetLogger Log = new BlueNetLogger();
    private static final String TAG = "BlueNetBluetoothWriter";

    private int id = -1;
    private boolean running = false;
    private StreamConnection sc = null;
    private NetworkStatistics netStats = null;
    private BluetoothConnectionManager btManager = null;
    private BlueNetNetworkHandler bnNetworkHandler = null;
    private LinkedBlockingQueue<BlueNetPacket> packetSendQueue = null;

    public BlueNetBluetoothWriter(
        int id,
        BluetoothConnectionManager btManager,
        LinkedBlockingQueue<BlueNetPacket> packetSendQueue,
        BlueNetNetworkHandler bnNetworkHandler,
        NetworkStatistics netStats)
    {
        this.running = true;
        this.id = id;
        this.btManager = btManager;
        this.packetSendQueue = packetSendQueue;
        this.bnNetworkHandler = bnNetworkHandler;
        this.netStats = netStats;
    }

    public StreamConnection getMainStreamConnection()
    {
        return this.sc;
    }

    public void sendPacket(BlueNetPacket curPacket)
    {
        this.packetSendQueue.offer(curPacket);
    }

    public void writePacket(ObjectOutputStream dataOutput, BlueNetPacket curPacket) throws Exception
    {
        if (curPacket.getPacketType() == BlueNetPacket.BN_PACKET_PING)
        {
            curPacket.setPingTime();
            this.netStats.incrementNumPingsSent();
        }
        else if (curPacket.getPacketType() == BlueNetPacket.BN_PACKET_PONG)
        {
            this.netStats.incrementNumPongsSent();
        }

        dataOutput.writeUnshared(curPacket);
        dataOutput.flush();
        dataOutput.reset();

        this.netStats.addBluetoothBytesWritten(curPacket.getDataLength());

        Log.d(TAG, "[" + this.id + "] Wrote " + curPacket);
        this.bnNetworkHandler.disposePacket(curPacket);        
    }

    public void run()
    {
        BluetoothConnection bc = null;
        try
        {
            Log.d(TAG, "[" + this.id + "] ***** BlueNetBluetoothWriter thread started *****");
            bc = this.btManager.acquireBluetoothConnection();
            this.sc = bc.sc;

            OutputStream os = this.sc.openOutputStream();
            BufferedOutputStream bufferedOS = new BufferedOutputStream(os);
            ObjectOutputStream dataOutput = new ObjectOutputStream(bufferedOS);

            Log.d(TAG, "[" + this.id + "] About to start main loop ...");
            while(this.running == true)
            {
                BlueNetPacket curPacket = this.packetSendQueue.take();

                // clear out any queued tags matching a packet that the client has closed
                this.bnNetworkHandler.clearClosedTagsIfAny(curPacket, this.packetSendQueue);

                // combine any packets of the same tag to minimize bluetooth writes
                this.bnNetworkHandler.coalescePackets(curPacket, this.packetSendQueue);

                // update the size of the queue so the network handler can adjust
                // throttled connections as needed
                this.bnNetworkHandler.updateQueueSize(packetSendQueue.size());

                //finally write the packet
                this.writePacket(dataOutput, curPacket);
            }
            Log.d(TAG, "[" + this.id + "] Exited from main loop");
        }
        catch(Exception e)
        {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            Log.e(TAG, "[" + this.id + "] Fatal error: " + sw.toString());
        }
        finally
        {
            if (bc != null)
            {
                this.btManager.releaseBluetoothConnection(bc);
            }
        }
        Log.e(TAG, "[" + this.id + "] ***** BlueNetBluetoothWriter terminating *****");
    }
}
