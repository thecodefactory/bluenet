package com.nm.bluenet;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.LinkedBlockingQueue;

import com.nm.bluenetcommon.BlueNetPacket;

public class BlueNetDNSHandler extends Thread
{
    private BlueNetLogger Log = new BlueNetLogger();
    private static final String TAG = "BlueNetDNSHandler";

    private final int MAX_UDP_BUF_LEN = 1024;

    // default is google's open 8.8.8.8 DNS server
    private final int defaultDNSPort = 53;
    private final String defaultDNSServer = "8.8.8.8";

    private int dnsPort = 0;
    private String dnsServer = null;
    private boolean running = false;
    private NetworkStatistics netStats = null;
    private LinkedBlockingQueue<BlueNetPacket> dnsPacketQueue = null;
    private LinkedBlockingQueue<BlueNetPacket> packetSendQueue = null;

    public BlueNetDNSHandler(
        LinkedBlockingQueue<BlueNetPacket> dnsPacketQueue,
        LinkedBlockingQueue<BlueNetPacket> packetSendQueue,
        NetworkStatistics netStats)
    {
        this.dnsPacketQueue = dnsPacketQueue;
        this.packetSendQueue = packetSendQueue;
        this.netStats = netStats;
        this.dnsPort = defaultDNSPort;
        this.dnsServer = defaultDNSServer;
    }

    public void setDNSServer(String hostname, int port)
    {
        this.dnsServer = hostname;
        this.dnsPort = port;
    }

    private BlueNetPacket handleDNSRequest(BlueNetPacket curPacket)
        throws Exception
    {
        byte[] buffer = new byte[MAX_UDP_BUF_LEN];

        // prepare data for dns server
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName(this.dnsServer);
        DatagramPacket dnsPacket = new DatagramPacket(
            curPacket.getData(), curPacket.getDataLength(), address, this.dnsPort);

        socket.send(dnsPacket);

        this.netStats.addNetBytesWritten(curPacket.getDataLength());
        this.netStats.incrementNumDNSRequestsReceived();

        Log.d(TAG, "DNS Packet sent to " + this.dnsServer + ", port " + this.dnsPort);

        // listen inline here for response
        DatagramPacket dnsResponse = new DatagramPacket(buffer, buffer.length);
        socket.receive(dnsResponse);

        Log.d(TAG, "DNS Response received of size: " + dnsResponse.getLength());

        this.netStats.addNetBytesRead(dnsResponse.getLength());
        this.netStats.incrementNumDNSRequestsHandled();

        BlueNetPacket respPacket = new BlueNetPacket(
            curPacket.getTag(), curPacket.getPacketType());
        respPacket.setData(dnsResponse.getData(), dnsResponse.getLength());
        respPacket.setDNSPort(curPacket.getDNSPort());
        respPacket.setDNSAddress(curPacket.getDNSAddress());

        Log.d(TAG, "Have DNS response packet: " + respPacket);
        return respPacket;
    }

    public void shutdown()
    {
        this.running = false;
    }

    public void run()
    {
        try
        {
            this.running = true;
            Log.d(TAG, "***** BlueNetDNSHandler thread started *****");

            while(this.running == true)
            {
                BlueNetPacket curPacket = this.dnsPacketQueue.take();
                BlueNetPacket respPacket = this.handleDNSRequest(curPacket);

                this.packetSendQueue.offer(respPacket);

                Log.d(TAG, "DNS Request answered");
            }
        }
        catch(Exception e)
        {
            Log.e(TAG, "DNS Handling Failure: " + e);
        }
        Log.d(TAG, "***** BlueNetDNSHandler thread terminating *****");
    }
}
