package com.nm.bluenet;

import com.nm.bluenetcommon.BlueNetNetworkStatistics;

public class NetworkStatistics implements BlueNetNetworkStatistics
{
	private int numBluetoothConnections = 0;
    private int numReadActiveBluetoothConnections = 1;
    private int numWriteActiveBluetoothConnections = 1;
    private int numReadWaitingBluetoothConnections = 0;
    private int numWriteWaitingBluetoothConnections = 0;

    private int numNetConnectionsConnected = 0;
    private int numHTTPSocketReadersActive = 0;
    private int numHTTPSSocketReadersActive = 0;

    private long lastPingTime = 0;

    private int numPingsSent = 0;
    private int numPingsReceived = 0;
    private int numPongsSent = 0;
    private int numPongsReceived = 0;

    private long netBytesRead = 0;
    private long netBytesWritten = 0;
    private long bluetoothBytesRead = 0;
    private long bluetoothBytesWritten = 0;
    private long numDNSRequestsReceived = 0;
    private long numDNSRequestsHandled = 0;

    public NetworkStatistics()
    {
    }

    public int getNumBluetoothConnections()
    {
        return this.numBluetoothConnections;
    }

    public int getNumReadActiveBluetoothConnections()
    {
        return this.numReadActiveBluetoothConnections;
    }

    public int getNumReadWaitingBluetoothConnections()
    {
        return this.numReadWaitingBluetoothConnections;
    }

    public int getNumWriteActiveBluetoothConnections()
    {
        return this.numWriteActiveBluetoothConnections;
    }

    public int getNumWriteWaitingBluetoothConnections()
    {
        return this.numWriteWaitingBluetoothConnections;
    }

    public int getNumHTTPSocketReadersActive()
    {
        return this.numHTTPSocketReadersActive;
    }

    public int getNumHTTPSSocketReadersActive()
    {
        return this.numHTTPSSocketReadersActive;
    }

    public long getNetBytesRead()
    {
        return this.netBytesRead;
    }

    public long getNetBytesWritten()
    {
        return this.netBytesWritten;
    }

    public long getBluetoothBytesRead()
    {
        return this.bluetoothBytesRead;
    }

    public long getBluetoothBytesWritten()
    {
        return this.bluetoothBytesWritten;
    }

    public long getDNSRequestsReceived()
    {
        return this.numDNSRequestsReceived;
    }

    public long getDNSRequestsHandled()
    {
        return this.numDNSRequestsHandled;
    }

    synchronized public void addNetBytesRead(int nRead)
    {
        this.netBytesRead += (long)nRead;
    }

    synchronized public void setNumBluetoothConnections(int nbc)
    {
        this.numBluetoothConnections = nbc;
    }

    synchronized public void addNetBytesWritten(int nWritten)
    {
        this.netBytesWritten += (long)nWritten;
    }

    synchronized public void addBluetoothBytesRead(int nRead)
    {
        this.bluetoothBytesRead += (long)nRead;
    }

    synchronized public void addBluetoothBytesWritten(int nWritten)
    {
        this.bluetoothBytesWritten += (long)nWritten;
    }

    synchronized public void incrementNumDNSRequestsReceived()
    {
        this.numDNSRequestsReceived++;
    }

    synchronized public void incrementNumDNSRequestsHandled()
    {
        this.numDNSRequestsHandled++;
    }

    synchronized public void setBluetoothReadActive()
    {
        this.numReadActiveBluetoothConnections++;
        this.numReadWaitingBluetoothConnections--;
    }

    synchronized public void setBluetoothReadWaiting()
    {
        this.numReadWaitingBluetoothConnections++;
        this.numReadActiveBluetoothConnections--;
    }

    synchronized public void setBluetoothWriteActive()
    {
        this.numWriteActiveBluetoothConnections++;
        this.numWriteWaitingBluetoothConnections--;
    }

    synchronized public void setBluetoothWriteWaiting()
    {
        this.numWriteWaitingBluetoothConnections++;
        this.numWriteActiveBluetoothConnections--;
    }

    synchronized public void incrementNumHTTPSocketReadersActive()
    {
        this.numHTTPSocketReadersActive++;
    }

    synchronized public void decrementNumHTTPSocketReadersActive()
    {
        this.numHTTPSocketReadersActive--;
    }

    synchronized public void incrementNumHTTPSSocketReadersActive()
    {
        this.numHTTPSSocketReadersActive++;
    }

    synchronized public void decrementNumHTTPSSocketReadersActive()
    {
        this.numHTTPSSocketReadersActive--;
    }

    @Override
    synchronized public void incrementNetConnectionsAccepted()
    {
    }

    @Override
    synchronized public void incrementNetConnectionsConnected()
    {
        this.numNetConnectionsConnected++;
    }

    @Override
    synchronized public void incrementNumPingsSent()
    {
        this.numPingsSent++;
    }

    @Override
    synchronized public void incrementNumPongsReceived()
    {
        this.numPongsReceived++;
    }

    @Override
    public void setLastPingTime(long pingTimeMillis)
    {
        this.lastPingTime = pingTimeMillis;
    }

    @Override
    public long getLastPingTime()
    {
        return this.lastPingTime;
    }

    @Override
    public int getNumNetConnectionsAccepted()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getNumNetConnectionsConnected()
    {
        return this.numNetConnectionsConnected;
    }

    @Override
    public int getNumPingsSent()
    {
        return this.numPingsSent;
    }


    @Override
    public int getNumPingsReceived()
    {
        return this.numPingsReceived;
    }

    @Override
    public int getNumPongsSent()
    {
        return this.numPingsSent;
    }

    @Override
    public int getNumPongsReceived()
    {
        return this.numPongsReceived;
    }

    @Override
    synchronized public void incrementNumPingsReceived()
    {
        this.numPingsReceived++;
    }

    @Override
    synchronized public void incrementNumPongsSent()
    {
        this.numPongsSent++;
    }

    public String toString()
    {
        StringBuffer str = new StringBuffer("\n------------ Network Statistics ------------");
        str.append("\nBytes read from phone network      : " + this.netBytesRead);
        str.append("\nBytes written to phone network     : " + this.netBytesWritten);
        str.append("\nBytes read from bluetooth client   : " + this.bluetoothBytesRead);
        str.append("\nBytes written to bluetooth client  : " + this.bluetoothBytesWritten);
        str.append("\nPing Requests sent                 : " + this.numPingsSent);
        str.append("\nPong Requests received             : " + this.numPongsReceived);
        str.append("\nLast Ping Time (in Milliseconds)   : " + this.lastPingTime);
        str.append("\nDNS Requests received              : " + this.numDNSRequestsReceived);
        str.append("\nDNS Requests handled               : " + this.numDNSRequestsHandled);
//        str.append("\nRead Active Bluetooth Connections  : " + this.numReadActiveBluetoothConnections);
//        str.append("\nRead Waiting Bluetooth Connections : " + this.numReadWaitingBluetoothConnections);
//        str.append("\nWrite Active Bluetooth Connections : " + this.numWriteActiveBluetoothConnections);
//        str.append("\nWrite Waiting Bluetooth Connections: " + this.numWriteWaitingBluetoothConnections);
//        str.append("\nHTTP Reader Threads Active         : " + this.numHTTPSocketReadersActive);
//        str.append("\nHTTPS Reader Threads Active        : " + this.numHTTPSSocketReadersActive);
        str.append("\n-------------------------------------------------\n");
        return str.toString();
    }
}
