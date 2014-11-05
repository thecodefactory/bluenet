package com.nm.bluenetconnect;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;

import com.nm.bluenetcommon.BlueNetPacket;

// reads UDP DNS data, constructs BlueNetPackets, and places them on
// a packetSendQueue so they're shipped via Bluetooth like any other
// data we receive; then we read them back from the appropriate dnsQueue
// and ship them back to the original DNS client. each request in handled
// synchronously, as not handling them this way causes failures when
// sending the final response back

public class BlueNetDNSHandler extends Thread
{
    BlueNetLogger Log = new BlueNetLogger();
    private static final String TAG = "BlueNetDNSHandler";

    private int port = 53;
    private NetworkStatistics netStats;
    private DatagramSocket serverSocket = null;
    private LinkedBlockingQueue<BlueNetPacket> dnsSendQueue = null;
    private Vector<LinkedBlockingQueue<BlueNetPacket>> packetSendQueueVec = null;

    public BlueNetDNSHandler(
        int port,
        LinkedBlockingQueue<BlueNetPacket> dnsSendQueue,
        Vector<LinkedBlockingQueue<BlueNetPacket>> packetSendQueueVec,
        NetworkStatistics netStats)
    {
        this.port = port;
        this.dnsSendQueue = dnsSendQueue;
        this.packetSendQueueVec = packetSendQueueVec;
        this.netStats = netStats;
    }

    public void run()
    {
        int nRead = 0;
        final int MAX_UDP_BUF_LEN = 1024;
        byte[] buffer = new byte[MAX_UDP_BUF_LEN];

        try
        {
            this.serverSocket = new DatagramSocket(this.port);
        }
        catch(Exception e)
        {
            Log.e(TAG, "Failed to start Server: " + e);
            return;
        }

        while(true)
        {
            try
            {
                DatagramPacket dnsPacket = new DatagramPacket(buffer, buffer.length);
                Log.i(TAG, "Waiting for new UDP data");

                this.serverSocket.receive(dnsPacket);
                nRead = dnsPacket.getLength();
                if (nRead == 0)
                {
                    Log.i(TAG, "Empty UDP Packet received -- ignoring");
                    continue;
                }

                this.netStats.addNetBytesRead(nRead);
                this.netStats.incrementNumDNSRequestsReceived();

                BlueNetPacket curPacket = new BlueNetPacket(
                    Integer.MAX_VALUE, BlueNetPacket.BN_PACKET_DNS);
                curPacket.setData(dnsPacket.getData(), dnsPacket.getLength());
                curPacket.setDNSPort(dnsPacket.getPort());
                curPacket.setDNSAddress(dnsPacket.getAddress());

                Log.i(TAG, "GOT: " + curPacket);

                // set up packet to be shipped
                LinkedBlockingQueue<BlueNetPacket> packetSendQueue =
                    BlueNetConnect.getShortestQueue(this.packetSendQueueVec);
                packetSendQueue.offer(curPacket);

                // wait for response inline here
                BlueNetPacket respPacket = this.dnsSendQueue.take();

                Log.i(TAG, "Got Response: " + respPacket);

                DatagramPacket sendPacket = new DatagramPacket(
                    respPacket.getData(), respPacket.getDataLength(),
                    dnsPacket.getAddress(), dnsPacket.getPort());

                this.serverSocket.send(sendPacket);

                this.netStats.addNetBytesWritten(respPacket.getDataLength());
                this.netStats.incrementNumDNSRequestsHandled();

                Log.i(TAG, "Answered DNS data (" + respPacket.getDataLength() + " bytes)");
            }
            catch(IOException e)
            {
                Log.e(TAG, "Failed: " + e + " -- Ignoring");
                e.printStackTrace();
            }
            catch(Exception e)
            {
                Log.e(TAG, "Failed: " + e);
                break;
            }
        }
        try
        {
            this.serverSocket.close();
        }
        catch(Exception e) {}
        Log.i(TAG, "BlueNetDNSHandler Server exiting normally");
    }
}
