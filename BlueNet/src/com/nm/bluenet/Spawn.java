package com.nm.bluenet;

import com.nm.bluenetcommon.BlueNetPacket;
import com.nm.bluenetcommon.BlueNetPacketAllocator;
import com.nm.bluenetcommon.BlueNetPingSender;
import com.nm.bluenetcommon.HTTPSTagManager;
import com.nm.bluenetcommon.Indexer;
import com.nm.bluenetcommon.ObjectManager;
import com.nm.bluenetcommon.PacketQueueManager;

import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;
import java.util.Map.Entry;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class Spawn extends Activity implements Indexer
{
    private BlueNetLogger Log = new BlueNetLogger();
	private static final String TAG = "Spawn";

	private static final int BT_UNSUPPORTED    = 0xF0;
	private static final int BT_NOTENABLED     = 0xF1;
	private static final int BT_ENABLED        = 0xF2;
	private static final int REQUEST_ENABLE_BT = 0xF3;

    private static final String BT_NOT_SUPPORTED_ERROR =
		"It appears that Bluetooth is not supported on your device." +
		"Unfortunately, there's nothing we can do here :-(";
	private static final String BT_NOT_ENABLED_ERROR =
		"It appears that Bluetooth is not enabled on your device." +
		"Unfortunately, there's nothing we can do here :-(";

	private ImageView imageView = null;

	private long lastNBRead = 0;
	private long lastNBWritten = 0;
    private long lastBBRead = 0;
    private long lastBBWritten = 0;
	private String status = "BlueNet Status: Offline\nInitializing BlueNet ...";
	private boolean showDetails = false;
	private Handler handler = null;
	private NetworkStatistics netStats = null;
	private BluetoothAdapter adapter = null;
	private ObjectManager channelManager = null;
	private HTTPSTagManager httpsTagManager = null;
	private boolean statusUpdatesRunning = false;
    private static Map<String, UUID> btServerNameUUIDMap = null;
    private BlueNetNetworkHandler bnNetworkHandler = null;
    private BlueNetPacketAllocator bnPacketAllocator = null;
    private Vector<BluetoothServerSocket> btSockets = null;
    private Vector<BlueNetBluetoothReader> btReaders = null;
    private Vector<BlueNetBluetoothWriter> btWriters = null;
    private Vector<BlueNetPingSender> bnPingSenders = null;
    private Vector<BlueNetDNSHandler> dnsHandlers = null;
    private Vector<LinkedBlockingQueue<BlueNetPacket>> dnsSendQueueVec = null;
    private Vector<LinkedBlockingQueue<BlueNetPacket>> packetSendQueueVec = null;
    private Vector<LinkedBlockingQueue<Integer>> closedTagQueueVec = null;

    public void continueServiceStartup()
	{
        try
        {
            Spawn.btServerNameUUIDMap = new HashMap<String, UUID>();
            Spawn.btServerNameUUIDMap.put("BlueNet-BT-Server-01",
                UUID.fromString("2f0cdc23-6516-44cb-a5f6-7c37694770d3"));
//            Spawn.btServerNameUUIDMap.put("BlueNet-BT-Server-02",
//                UUID.fromString("6e587a4c-e6e5-11e0-b79e-97d8c2867dcc"));
//            Spawn.btServerNameUUIDMap.put("BlueNet-BT-Server-03",
//                UUID.fromString("69d47a1e-e6ec-11e0-97e2-ab39b340c638"));
//            Spawn.btServerNameUUIDMap.put("BlueNet-BT-Server-04",
//                UUID.fromString("6accba3a-e6ec-11e0-888f-1b2cfe02caf7"));

            Lock connectLock = new ReentrantLock();
            this.netStats = new NetworkStatistics();
            this.channelManager = new ObjectManager();
            this.httpsTagManager = new HTTPSTagManager();
            Selector selector = SelectorProvider.provider().openSelector();
            PacketQueueManager packetQueueManager = new PacketQueueManager();
            LinkedBlockingQueue<BlueNetPacket> dnsPacketQueue =
                new LinkedBlockingQueue<BlueNetPacket>();

            this.bnPacketAllocator = new
                BlueNetPacketAllocator(Spawn.btServerNameUUIDMap.size());
            this.btSockets = new Vector<BluetoothServerSocket>();
            this.btReaders = new Vector<BlueNetBluetoothReader>();
            this.btWriters = new Vector<BlueNetBluetoothWriter>();
            this.bnPingSenders = new Vector<BlueNetPingSender>();
            this.dnsHandlers = new Vector<BlueNetDNSHandler>();

            this.dnsSendQueueVec = new Vector<LinkedBlockingQueue<BlueNetPacket>>();
            this.packetSendQueueVec = new Vector<LinkedBlockingQueue<BlueNetPacket>>();
            this.closedTagQueueVec = new Vector<LinkedBlockingQueue<Integer>>();

            this.bnNetworkHandler = new BlueNetNetworkHandler(
                this, channelManager, selector, connectLock, httpsTagManager,
                packetQueueManager, packetSendQueueVec, this.bnPacketAllocator,
                closedTagQueueVec, netStats);

            int id = 0;
			for(Entry<String, UUID> curEntry : Spawn.btServerNameUUIDMap.entrySet())
			{
			    String name = curEntry.getKey();
			    UUID uuid = curEntry.getValue();

			    BluetoothServerSocket btSocket =
			        this.adapter.listenUsingRfcommWithServiceRecord(name, uuid);

                LinkedBlockingQueue<BlueNetPacket> dnsSendQueue =
                    new LinkedBlockingQueue<BlueNetPacket>();
			    LinkedBlockingQueue<BlueNetPacket> packetSendQueue =
			        new LinkedBlockingQueue<BlueNetPacket>();
			    LinkedBlockingQueue<Integer> closedTagQueue =
			        new LinkedBlockingQueue<Integer>();

		        // reads packets from bluetooth and places them either in the
	            // socketChannel writeList (HTTP/HTTPS), the dnsPacketQueue (DNS),
			    // or handling a ping operation inline
			    BlueNetBluetoothReader btReader = new BlueNetBluetoothReader(
			        id, btSocket, channelManager, selector, connectLock,
			        httpsTagManager,  packetQueueManager, packetSendQueue,
			        dnsPacketQueue, this.netStats);

	            // pulls packets from the packetSendQueue and writes the packets over bluetooth.
	            // requires the reader thread only to retrieve the main bluetooth socket
			    BlueNetBluetoothWriter btWriter = new BlueNetBluetoothWriter(
			        id, btReader, packetSendQueue, bnNetworkHandler, this.netStats);

			    BlueNetPingSender pingSender = new BlueNetPingSender(
			        id, this.Log, packetSendQueue, this.bnPacketAllocator, this.netStats);

			    // handles incoming DNS lookups
			    // FIXME: need to consolidate this into a single DNS queue??
	            BlueNetDNSHandler dnsHandler = new BlueNetDNSHandler(
	                dnsPacketQueue, packetSendQueue, this.netStats);

	            // FIXME: Start up ping handlers
	            // Have a ping handler thread per queue?  That way we can see
	            // how each queue is performing individually

	            this.btSockets.add(btSocket);
			    this.btReaders.add(btReader);
			    this.btWriters.add(btWriter);
			    this.bnPingSenders.add(pingSender);
			    this.dnsHandlers.add(dnsHandler);

			    this.dnsSendQueueVec.add(dnsSendQueue);
			    this.packetSendQueueVec.add(packetSendQueue);
			    this.closedTagQueueVec.add(closedTagQueue);

			    btReader.start();
			    btWriter.start();
			    pingSender.start();
                dnsHandler.start();

			    id++;
			}

			this.bnNetworkHandler.start();

            Log.d(TAG, "Started all Bluetooth and Net Readers and Writers");

            this.updateStatusString("BlueNet Status: Offline\nWaiting for BlueNetConnect to respond ...");

            this.startStatusUpdates();
        }
        catch (Exception e)
		{
        	Log.d(TAG, "FAILURE: " + e);
		}
	}

	public int getQueueIndexByTag(int tag)
	{
	    int len = btServerNameUUIDMap.size();
	    return (tag % len);
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
	        if (curLen < shortestIndex)
	        {
	            shortestIndex = i;
	        }
	    }
	    return queueVec.get(shortestIndex);
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if (requestCode == REQUEST_ENABLE_BT)
		{
			Log.d(TAG, "GOT PROPER RESULT, with RESULT CODE = " + resultCode);
            // Bluetooth Discoverable Mode does not return the standard
            // Activity result codes. The result code is the duration (seconds) of
            // discoverability or a negative number if the user answered "NO".
	        if (resultCode > 0)
			{
	        	Toast.makeText(this, "Bluetooth Enabled", Toast.LENGTH_LONG).show();
	        	continueServiceStartup();
			}
	        else
	        {
	        	showFatalBluetoothError(BT_NOT_ENABLED_ERROR);
	        }
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		Log.d(TAG, "IGNORING CONFIGURATION CHANGE");
	}

	private int initialize()
	{
		int ret = BT_UNSUPPORTED;
		this.adapter = BluetoothAdapter.getDefaultAdapter();
		if (this.adapter != null)
		{
			if (!this.adapter.isEnabled() || !this.adapter.isDiscovering())
			{
				ret = BT_NOTENABLED;
			    Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			    startActivityForResult(discoverableIntent, REQUEST_ENABLE_BT);
			}
			else
			{
				ret = BT_ENABLED;
			}
		}
		return ret;
	}

	public void showFatalBluetoothError(String str)
	{
    	AlertDialog.Builder dlg = new AlertDialog.Builder(this);
		dlg.setTitle("Bluetooth Error");
		dlg.setMessage(str);
		dlg.setIcon(android.R.drawable.ic_dialog_alert);
		dlg.setPositiveButton("Ok", new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int whichButton)
			{
				dialog.dismiss();
				finish();
			}
		});
		dlg.show();
	}

	public String getBTStatusString(int code)
	{
		String ret = null;
		switch(code)
		{
		case BT_UNSUPPORTED:
			ret = "BT_UNSUPPORTED";
			break;
		case BT_NOTENABLED:
			ret = "BT_NOTENABLED";
			break;
		case BT_ENABLED:
			ret = "BT_ENABLED";
			break;
		}
		return ret;
	}

	synchronized public void updateStatus()
	{
	    if (this.imageView != null)
	    {
	        long pingTime = this.netStats.getLastPingTime();
    	    boolean isConnected = ((this.netStats.getNumPongsReceived() > 0) &&
    	                            pingTime < 60000);

//    	    Log.d(TAG, "IsConnected = " + isConnected + " (LastPingTime = " +
//    	          pingTime + ")");

    	    if (isConnected == true)
    	    {
                if (pingTime > 30000)
                {
                    this.imageView.setImageResource(
                        ((isConnected == true) ? R.drawable.bnyellow : R.drawable.bnred));
                }
                else
                {
                    this.imageView.setImageResource(
                        ((isConnected == true) ? R.drawable.bngreen : R.drawable.bnred));
                }

    	        long nbRead = this.netStats.getNetBytesRead();
                long nbWritten = this.netStats.getNetBytesWritten();

                long newDataRead = nbRead - this.lastNBRead;
                long newDataWritten = nbWritten - this.lastNBWritten;

                int dbw = (int)((newDataRead > 0) ? (newDataRead / 1024) : 0);
                int ubw = (int)((newDataWritten > 0) ? (newDataWritten / 1024) : 0);

                this.lastNBRead = nbRead;
                this.lastNBWritten = nbWritten;

                long bbRead = this.netStats.getBluetoothBytesRead();
                long bbWritten = this.netStats.getBluetoothBytesWritten();

                long newBDataRead = bbRead - this.lastBBRead;
                long newBDataWritten = bbWritten - this.lastBBWritten;

                int bdbw = (int)((newBDataWritten > 0) ? (newBDataWritten / 1024) : 0);
                int bubw = (int)((newBDataRead > 0) ? (newBDataRead / 1024) : 0);

                this.lastBBRead = bbRead;
                this.lastBBWritten = bbWritten;

                this.status = "BlueNet Status: Online\nNet Bandwidth: " +
                    dbw + " KBps Down  |  " + ubw + " KBps Up\nBluetooth Bandwidth: " +
                    bdbw + " KBps Down  |  " + bubw + " KBps Up";
    	    }
    	    else
    	    {
    	        if (!this.status.startsWith("BlueNet Status: Offline"))
    	        {
    	            this.status = "BlueNet Status: Offline";
    	        }
    	        this.imageView.setImageResource(R.drawable.bnred);
    	    }
	    }

	    if (this.showDetails == true)
	    {
			final Date d = new Date();
			final CharSequence s  = DateFormat.format("EEEE, MMMM d, yyyy h:mm:ss aa ", d.getTime());

			StringBuffer str = new StringBuffer(s);
			str.append("\n\nBluetooth Connections In Use: ");
			str.append(this.btSockets.size());
            str.append("\nNetwork Connections In Use: ");
            str.append(this.channelManager.getNumObjects());
            str.append("\nSecure Network Connections In Use: ");
            str.append(this.httpsTagManager.getNumHTTPSConnections());
            str.append("\n");
			str.append(this.netStats.toString());
            str.append("\n\n***** Internal Queue Live Statistics *****\n\n");
            int numQueues = this.packetSendQueueVec.size();
            for(int i = 0; i < numQueues; i++)
            {
                str.append("BlueNetPacket Pool Size is ");
                str.append(this.bnPacketAllocator.getPacketPoolSize());
                str.append("\n");

                str.append("Bluetooth Packet Send Queue[");
                str.append(i);
                str.append("] size is ");
                str.append(this.packetSendQueueVec.get(i).size());
                str.append("\n");

                str.append("DNS Packet Send Queue[");
                str.append(i);
                str.append("] size is ");
                str.append(this.dnsSendQueueVec.get(i).size());
                str.append("\n");
            }
            this.updateStatusString(str.toString());
	    }
	    else
	    {
	        this.updateStatusString(this.status);
	    }
	}

	public void startStatusUpdates()
	{
		if (this.statusUpdatesRunning == false)
		{
		    this.handler = new Handler();
			Runnable runnable = new Runnable() {
				@Override
				public void run() {
				    Runnable tmpRunnable = new Runnable() {
                        @Override
                        public void run() {
                            updateStatus();
                            return;
                        }
                    };
					do
					{
						handler.post(tmpRunnable);	
						try { Thread.sleep(1000); } catch (Exception e) {}
					} while(true);
				}
			};
			new Thread(runnable).start();
			this.statusUpdatesRunning  = true;
		}
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		this.shutdown();
	}

	public void shutdown()
	{
	    this.status = "Shutting down BlueNet ...";

	    // close all bluetooth server sockets
        for(BluetoothServerSocket btSocket : this.btSockets)
        {
            try { btSocket.close(); } catch(Exception e) {}
        }

        // FIXME: We need to ask the user if this is OK,
        // and then store that setting in preferences
        if (this.adapter != null)
        {
            this.adapter.disable();
        }

        // shutdown all threaded services
        this.bnNetworkHandler.shutdown();

        for(BlueNetBluetoothReader btReader : this.btReaders)
        {
            btReader.shutdown();
        }
        for(BlueNetBluetoothWriter btWriter : this.btWriters)
        {
            btWriter.shutdown();
        }
        for(BlueNetPingSender bnPingSenders : this.bnPingSenders)
        {
            bnPingSenders.shutdown();
        }
        for(BlueNetDNSHandler dnsHandler : this.dnsHandlers)
        {
            dnsHandler.shutdown();
        }

        // clear any remaining packet queues
        for(LinkedBlockingQueue<BlueNetPacket> dnsSendQueue : this.dnsSendQueueVec)
        {
            dnsSendQueue.clear();
        }
        for(LinkedBlockingQueue<BlueNetPacket> packetSendQueue : this.packetSendQueueVec)
        {
            packetSendQueue.clear();
        }
        for(LinkedBlockingQueue<Integer> closedTagQueue : this.closedTagQueueVec)
        {
            closedTagQueue.clear();
        }
        Log.d(TAG, "***** BlueNet Teardown Complete *****");
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.toggle_details:
            {
                this.showDetails = (!this.showDetails);
                return true;
            }
            case R.id.exit:
            {
                this.shutdown();
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void updateStatusString(String str)
    {
        TextView status = (TextView)findViewById(R.id.status);
        if (status != null)
        {
            if (str != null)
            {
                this.status = str;
            }
            status.setText(this.status);
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        this.imageView = (ImageView)findViewById(R.id.background);
        this.updateStatusString(this.status);

        int ret = initialize();
        Log.d(TAG, "INITIALIZE RETURNED " + getBTStatusString(ret));
        if (ret == BT_UNSUPPORTED)
        {
        	showFatalBluetoothError(BT_NOT_SUPPORTED_ERROR);
        }
        else if (ret == BT_ENABLED)
        {
	        continueServiceStartup(); 	
        }
    }
}