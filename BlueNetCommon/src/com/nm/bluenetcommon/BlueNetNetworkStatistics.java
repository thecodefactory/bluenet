package com.nm.bluenetcommon;

public interface BlueNetNetworkStatistics
{
    public void addNetBytesRead(int nRead);
    public void addNetBytesWritten(int nWritten);

    public long getNetBytesRead();
    public long getNetBytesWritten();

    public void incrementNetConnectionsAccepted();
    public void incrementNetConnectionsConnected();

    public int getNumNetConnectionsAccepted();
    public int getNumNetConnectionsConnected();
    
    public void incrementNumPingsSent();
    public void incrementNumPingsReceived();
    public void incrementNumPongsSent();
    public void incrementNumPongsReceived();

    public void setLastPingTime(long pingTimeMillis);
    public long getLastPingTime();

    public int getNumPingsSent();
    public int getNumPingsReceived();
    public int getNumPongsSent();
    public int getNumPongsReceived();
}
