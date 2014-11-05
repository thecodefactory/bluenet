package com.nm.bluenet;

import java.io.BufferedOutputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.LinkedBlockingQueue;

import com.nm.bluenetcommon.BlueNetPacket;
import android.bluetooth.BluetoothSocket;

public class BlueNetBluetoothWriter extends Thread
{
    private BlueNetLogger Log = new BlueNetLogger();
	private static final String TAG = "BlueNetBluetoothWriter";

    private int id = -1;
	private boolean running = false;
	private BluetoothSocket socket = null;
    private NetworkStatistics netStats = null;
	private BlueNetBluetoothReader btReader = null;
    private BlueNetNetworkHandler bnNetworkHandler = null;
	private LinkedBlockingQueue<BlueNetPacket> packetSendQueue = null;

	public BlueNetBluetoothWriter(
        int id, BlueNetBluetoothReader btReader,
        LinkedBlockingQueue<BlueNetPacket> packetSendQueue,
        BlueNetNetworkHandler bnNetworkHandler,
        NetworkStatistics netStats)
	{
		this.running = true;
		this.id = id;
		this.btReader = btReader;
		this.packetSendQueue = packetSendQueue;
		this.bnNetworkHandler = bnNetworkHandler;
		this.netStats = netStats;
	}

    public void shutdown()
    {
        this.running = false;

        // should already be closed from the reader
        try { this.socket.close(); } catch(Exception e) {}
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
		Log.d(TAG, "[" + this.id + "] ***** BlueNetBluetoothWriter thread started *****");
		do
		{
			this.socket = this.btReader.getMainSocket();
			if (this.socket == null)
			{
				try { Thread.sleep(100); } catch(Exception e) {}
			}
		} while(this.socket == null);

		try
		{
            OutputStream os = this.socket.getOutputStream();
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
		Log.d(TAG, "[" + this.id + "] ***** BlueNetBluetoothWriter terminating *****");
	}
}
