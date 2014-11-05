package com.nm.bluenetconnect;

import javax.bluetooth.ServiceRecord;
import javax.bluetooth.DiscoveryAgent;

import java.util.concurrent.LinkedBlockingQueue;


public class BluetoothReInitThread extends Thread
{
    BlueNetLogger Log = new BlueNetLogger();
    private static final String TAG = "BluetoothReInitThread";

    private DiscoveryAgent agent = null;
    private LinkedBlockingQueue<BluetoothConnection> reInitQueue = null;
    private LinkedBlockingQueue<BluetoothConnection> availableConnectionQueue = null;
        
    public BluetoothReInitThread(
        DiscoveryAgent agent,
        LinkedBlockingQueue<BluetoothConnection> reInitQueue,
        LinkedBlockingQueue<BluetoothConnection> availableConnectionQueue)
    {
        this.agent = agent;
        this.reInitQueue = reInitQueue;
        this.availableConnectionQueue = availableConnectionQueue;
    }

    public void run()
    {
        while(true)
        {
            try
            {
                BluetoothConnection bc = this.reInitQueue.take();

                try { Thread.sleep(200); } catch(Exception e) {}

                Log.i(TAG, "About to selectService for " + bc.uuid);
                bc.url = this.agent.selectService(
                    bc.uuid, ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);

                if (bc.url == null)
                {
                    // if re-init fails, place this back on the reInitQueue
                    Log.e(TAG, "Failed -- adding back to reInitQueue");
                    this.reInitQueue.put(bc);
                }
                else
                {
                    // otherwise it's good to go!
                    Log.i(TAG, "Got new connection URL: " + bc.url);
                    this.availableConnectionQueue.offer(bc);
                }
            }
            catch(Exception e)
            {
                // FIXME: Possible connection lost forever; never seems to happen
                Log.e(TAG, "Failure: " + e);
            }
        }
    }
}
