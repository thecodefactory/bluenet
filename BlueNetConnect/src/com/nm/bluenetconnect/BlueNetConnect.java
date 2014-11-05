package com.nm.bluenetconnect;

import com.nm.bluenetcommon.BlueNetPacket;
import com.nm.bluenetcommon.BlueNetPacketAllocator;
import com.nm.bluenetcommon.Indexer;
import com.nm.bluenetcommon.ObjectManager;
import com.nm.bluenetcommon.PacketQueueManager;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;

import java.util.logging.Logger;

import javax.bluetooth.UUID;

public class BlueNetConnect implements Indexer
{
    BlueNetLogger Log = new BlueNetLogger();
    private static final String TAG = "BlueNetConnect";

    private static int numBTConnections = -1;
    private final NetworkStatistics netStats = new NetworkStatistics();

    public final NetworkStatistics getNetStats()
    {
        return this.netStats;
    }

    public int getQueueIndexByTag(int tag)
    {
        return (tag % BlueNetConnect.numBTConnections);
    }

    public LinkedBlockingQueue<BlueNetPacket> getQueueByTag(
        Vector<LinkedBlockingQueue<BlueNetPacket>> queueVec, int tag) throws Exception
    {
        return queueVec.get(getQueueIndexByTag(tag));
    }

    public static LinkedBlockingQueue<BlueNetPacket> getShortestQueue(
        Vector<LinkedBlockingQueue<BlueNetPacket>> queueVec) throws Exception
    {
        int shortestIndex = 99999, curLen = -1;
        final int vecLen = queueVec.size();
        for(int i = 0; i < vecLen; i++)
        {
            curLen = queueVec.get(i).size();
            if (curLen == 0)
            {
                shortestIndex = i;
                break;
            }
            if (curLen < shortestIndex)
            {
                shortestIndex = i;
            }
        }
        return queueVec.get(shortestIndex);
    }

    private void startBlueNet(int numBTConnections) throws IOException
    {
        BlueNetConnect.numBTConnections = numBTConnections;

        FileHandler fileLogger = new FileHandler("BlueNetConnectLog.txt");
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z |");
        fileLogger.setFormatter(new Formatter() {
                public String format(LogRecord rec)
                {
                    StringBuffer buf = new StringBuffer();
                    buf.append(sdf.format(new java.util.Date()));
                    buf.append(' ');
                    buf.append(rec.getLevel());
                    buf.append(' ');
                    buf.append(formatMessage(rec));
                    buf.append('\n');
                    return buf.toString();
                }
        });
        // suppress console output
        Logger rootLogger = Logger.getLogger("");
        Handler[] handlers = rootLogger.getHandlers();
        if (handlers[0] instanceof ConsoleHandler)
        {
            rootLogger.removeHandler(handlers[0]);
        }
        Log.addHandler(fileLogger);
        Log.i(TAG, "Logging Initialized");

        UUID[] uuidSet = new UUID[10];
        uuidSet[0] = new UUID("2f0cdc23651644cba5f67c37694770d3", false);
        uuidSet[1] = new UUID("6e587a4ce6e511e0b79e97d8c2867dcc", false);
        uuidSet[2] = new UUID("69d47a1ee6ec11e097e2ab39b340c638", false);
        uuidSet[3] = new UUID("6accba3ae6ec11e0888f1b2cfe02caf7", false);
        uuidSet[4] = new UUID("6be22f54e6ec11e0b0c8cbe1e0e1c0dd", false);
        uuidSet[5] = new UUID("322b2596e7a411e09539532a07de013b", false);
        uuidSet[6] = new UUID("32890332e7a411e0b93b4f0065593b35", false);
        uuidSet[7] = new UUID("32d9d708e7a411e0953fafeabbb0068a", false);
        uuidSet[8] = new UUID("332920f6e7a411e09f842f3f1cc395e5", false);
        uuidSet[9] = new UUID("3383a792e7a411e0b1a3cbb476b2075e", false);

        UUID[] actualUUIDs = new UUID[numBTConnections];
        for(int i = 0; i < numBTConnections; i++)
        {   
            actualUUIDs[i] = uuidSet[i];
        }

        Log.i(TAG, "Initializing Bluetooth Connection Manager");

        BluetoothConnectionManager btManager = new BluetoothConnectionManager(netStats);
        if (btManager.initializeConnectionURLs(actualUUIDs) == false)
        {
            Log.e(TAG, "ERROR: Could not find the BlueNet App.");
            Log.e(TAG, "       Please be sure it's running on the Android device and try again.");
            System.exit(0);
        }

        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            public void run()
            {   
                Log.i(TAG, netStats.toString());
                Log.i(TAG, "Shutting down BlueNet");
            }
        });

        ObjectManager channelManager = new ObjectManager();
        PacketQueueManager packetQueueManager = new PacketQueueManager();
        Selector selector = SelectorProvider.provider().openSelector();
        BlueNetPacketAllocator bnPacketAllocator = new BlueNetPacketAllocator(numBTConnections);

        Vector<LinkedBlockingQueue<Integer>> closedTagQueueVec =
            new Vector<LinkedBlockingQueue<Integer>>();
        Vector<LinkedBlockingQueue<BlueNetPacket>> packetSendQueueVec =
            new Vector<LinkedBlockingQueue<BlueNetPacket>>();

        LinkedBlockingQueue<BlueNetPacket> dnsSendQueue =
            new LinkedBlockingQueue<BlueNetPacket>();

        Vector<BlueNetBluetoothWriter> btWriters = new Vector<BlueNetBluetoothWriter>();
        Vector<BlueNetBluetoothReader> btReaders = new Vector<BlueNetBluetoothReader>();
        /* Vector<BlueNetPingSender> bnPingSenders = new Vector<BlueNetPingSender>(); */

        BlueNetNetworkHandler networkHandler = new BlueNetNetworkHandler(
            1927, this, channelManager, selector, packetQueueManager,
            packetSendQueueVec, bnPacketAllocator,
            closedTagQueueVec, this.netStats);

        for(int i = 0; i < numBTConnections; i++)
        {
            LinkedBlockingQueue<BlueNetPacket> packetSendQueue =
                new LinkedBlockingQueue<BlueNetPacket>();
            LinkedBlockingQueue<Integer> closedTagQueue =
                new LinkedBlockingQueue<Integer>();

            packetSendQueueVec.add(packetSendQueue);
            closedTagQueueVec.add(closedTagQueue);

            BlueNetBluetoothWriter btWriter = new BlueNetBluetoothWriter(
                i, btManager, packetSendQueue, networkHandler, this.netStats);
            BlueNetBluetoothReader btReader = new BlueNetBluetoothReader(
                i, btWriter, channelManager, selector, packetQueueManager, dnsSendQueue);
            /* BlueNetPingSender pingSender = new BlueNetPingSender( */
            /*     i, this.Log, packetSendQueue, bnPacketAllocator, this.netStats); */

            btWriters.add(btWriter);
            btReaders.add(btReader);
            /* bnPingSenders.add(pingSender); */

            packetSendQueueVec.add(packetSendQueue);

            Log.i(TAG, "***** Starting Bluetooth Writer Server " + i + " *****");
            btWriter.start();

            Log.i(TAG, "***** Starting Bluetooth Reader Server " + i + " *****");
            btReader.start();

            /* Log.i(TAG, "***** Starting Ping Sender " + i + " *****"); */
            /* pingSender.start(); */
        }

        BlueNetDNSHandler dnsHandler = new BlueNetDNSHandler(
            53, dnsSendQueue, packetSendQueueVec, this.netStats);

        Log.i(TAG, "***** Starting DNS Handler Server *****");
        dnsHandler.start();

        Log.i(TAG, "***** Starting Network Handler Server *****");
        networkHandler.start();

        Log.i(TAG, "BlueNetConnect Starting");
    }

    public static void main(String[] args)
    {
        BlueNetLogger Log = new BlueNetLogger();
        BlueNetConnect bnc = new BlueNetConnect();
        try
        {
            // FIXME: make configurable?
            bnc.startBlueNet(1);
        }
        catch (Exception e)
        {
            Log.e("BlueNetConnect", "Main Failure: " + e);
            e.printStackTrace();
        }
    }
}
