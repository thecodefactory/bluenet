package com.nm.bluenetconnect;

import javax.bluetooth.UUID;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.DiscoveryAgent;

import javax.microedition.io.*;

import java.util.concurrent.LinkedBlockingQueue;

public class BluetoothConnectionManager
{
    BlueNetLogger Log = new BlueNetLogger();
    private static final String TAG = "BluetoothConnectionManager";

    private int numConnections = 0;
    private DiscoveryAgent agent = null;
    private LocalDevice localDevice = null;
    private final NetworkStatistics netStats;
    private BluetoothReInitThread reInitThread = null;
    private LinkedBlockingQueue<BluetoothConnection> reInitQueue = null;
    private LinkedBlockingQueue<BluetoothConnection> availableConnectionQueue = null;

    public BluetoothConnectionManager(NetworkStatistics netStats)
    {
        this.netStats = netStats;

        this.reInitQueue = new LinkedBlockingQueue<BluetoothConnection>();
        this.availableConnectionQueue = new LinkedBlockingQueue<BluetoothConnection>();
    }

    public boolean initializeConnectionURLs(UUID[] uuidSet)
    {
        boolean ret = false;
        try
        {
            this.localDevice = LocalDevice.getLocalDevice();
            Log.i(TAG, "Found local Bluetooth device \"" + localDevice.getFriendlyName() +
                        "\" with address " + localDevice.getBluetoothAddress());
            this.agent = localDevice.getDiscoveryAgent();

            Log.i(TAG, "Starting Re-init thread(s) ...");

            this.reInitThread = new BluetoothReInitThread(
                this.agent, this.reInitQueue, this.availableConnectionQueue);
            this.reInitThread.start();

            Log.i(TAG, "Searching for the BlueNet App ...");

            BluetoothConnection bc = null;
            this.numConnections = uuidSet.length;
            for(int i = 0; i < this.numConnections; i++)
            {
                bc = new BluetoothConnection();
                bc.sc = null;
                bc.url = null;
                bc.uuid = uuidSet[i];

                this.reInitQueue.offer(bc);
            }

            this.netStats.setNumBluetoothConnections(this.numConnections);

            Log.i(TAG, "Initialized " + this.numConnections + " Bluetooth Connections");

            ret = (this.numConnections == uuidSet.length);
        }
        catch(Exception e)
        {
            Log.e(TAG, "Failed to initialize Bluetooth Connection Manager: " + e);
            e.printStackTrace();
        }
        return ret;
    }

    public BluetoothConnection acquireBluetoothConnection()
    {
        BluetoothConnection bc = null;
        do
        {
            try
            {
                bc = this.availableConnectionQueue.take();
                bc.sc = (StreamConnection)Connector.open(bc.url, Connector.READ_WRITE);
            }
            catch(Exception e)
            {
                if (bc != null)
                {
                    Log.i(TAG, "Failed to open connection: " + bc.url + " Exception: " + e);
                }

                bc.url = null;
                this.reInitQueue.offer(bc);

                bc = null;
            }
        } while(bc == null);

        Log.i(TAG, "Got new connection url: " + bc.url);
        this.netStats.incrementActiveBluetoothConnections();
        return bc;
    }

    public void releaseBluetoothConnection(BluetoothConnection bc)
    {
        Log.i(TAG, "Releasing connection url: " + bc.url);
        try
        {
            bc.sc.close();
            bc.sc = null;

            this.netStats.decrementActiveBluetoothConnections();
            this.reInitQueue.put(bc);
        }
        catch(Exception e)
        {
            // FIXME: Possible loss forever, though it doesn't seem to happen ever
            Log.e(TAG, "Failed to put connection on ReInitQueue: " + e);
        }
    }
}
