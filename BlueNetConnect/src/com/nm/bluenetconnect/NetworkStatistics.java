package com.nm.bluenetconnect;

import com.nm.bluenetcommon.BlueNetNetworkStatistics;

public class NetworkStatistics implements BlueNetNetworkStatistics
{
    private int numBluetoothConnections = 0;
    private int numActiveBluetoothConnections = 0;

    private long netBytesRead = 0;
    private long netBytesWritten = 0;
    private long netConnectionsAccepted = 0;

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

    public int getNumActiveBluetoothConnections()
    {
        return this.numActiveBluetoothConnections;
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

    public void setNumBluetoothConnections(int nbc)
    {
        this.numBluetoothConnections = nbc;
    }

    synchronized public void addNetBytesRead(int nRead)
    {
        this.netBytesRead += (long)nRead;
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

    synchronized public void incrementActiveBluetoothConnections()
    {
        this.numActiveBluetoothConnections++;
    }

    synchronized public void decrementActiveBluetoothConnections()
    {
        this.numActiveBluetoothConnections--;
    }

    synchronized public void incrementNetConnectionsAccepted()
    {
        this.netConnectionsAccepted++;
    }

    @Override
    public void incrementNetConnectionsConnected()
    {
    }

    @Override
    public long getLastPingTime()
    {
        // TODO Auto-generated method stub
        return 0;
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
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getNumPingsSent()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getNumPongsReceived()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void incrementNumPingsSent()
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public int getNumPingsReceived()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getNumPongsSent()
    {
        // TODO Auto-generated method stub
        return 0;
    }


    @Override
    public void incrementNumPingsReceived()
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void incrementNumPongsSent()
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void incrementNumPongsReceived()
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setLastPingTime(long pingTimeMillis)
    {
        // TODO Auto-generated method stub
        
    }

    public String toString()
    {
        StringBuffer str = new StringBuffer("\n------- Network Statistics -------");
        str.append("\nBrowser Connections Handled: " + this.netConnectionsAccepted);
        str.append("\nBytes read from browser    : " + this.netBytesRead);
        str.append("\nBytes written to browser   : " + this.netBytesWritten);
        /* str.append("\nBytes read from bluetooth  : " + this.bluetoothBytesRead); */
        /* str.append("\nBytes written to bluetooth : " + this.bluetoothBytesWritten); */
        /* str.append("\nDNS Requests received      : " + this.numDNSRequestsReceived); */
        str.append("\nDNS Requests handled       : " + this.numDNSRequestsHandled);
        str.append("\n----------------------------------\n");
        return str.toString();
    }
}
