package com.nm.bluenetcommon;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

public class BlueNetPacket implements Serializable
{
    private static final long serialVersionUID = -5912537637720349691L;

    public transient final static int BN_PACKET_UNKNOWN = 0xFF;
    public transient final static int BN_PACKET_HTTP    = 0xFE;
    public transient final static int BN_PACKET_HTTPS   = 0xFD;
    public transient final static int BN_PACKET_DNS     = 0xFC;
    public transient final static int BN_PACKET_PING    = 0xFB;
    public transient final static int BN_PACKET_PONG    = 0xFA;

    public transient final static int MAX_DATA_LEN = 32768;

    private transient long checksum = 0;
    private transient Checksum adChecksum = null;
    private transient boolean isPooled = false;

    private int tag = -1;
    private int len = -1;
    private int packetType = BN_PACKET_UNKNOWN;
    private boolean shouldClose = false;

    private byte[] data = new byte[BlueNetPacket.MAX_DATA_LEN];

    // fields only applicable to BN_PACKET_DNS types
    private int dnsPort = -1;
    private InetAddress dnsAddress = null;

    // fields only applicable to BN_PACKET_PING types
    private long pingSendTime = 0;

    public BlueNetPacket()
    {
        this.pingSendTime = System.currentTimeMillis();
    }

    public BlueNetPacket(int tag, int packetType)
    {
    	this.tag = tag;
    	this.packetType = packetType;
        this.pingSendTime = System.currentTimeMillis();
    }

    public BlueNetPacket(int tag, int packetType, byte[] data, int len)
    {
    	this.tag = tag;
    	this.packetType = packetType;
    	this.setData(data, len);
    	this.len = len;
        this.pingSendTime = System.currentTimeMillis();
    }

    public void clear()
    {
        this.tag = -1;
        this.packetType = BlueNetPacket.BN_PACKET_UNKNOWN;
        this.len = -1;
        this.shouldClose = false;
        this.isPooled = false;
        this.checksum = 0;
        this.pingSendTime = 0;
    }

    public void clear(byte[] nullData, int nullDataLen)
    {
        this.clear();
        this.setData(nullData, nullDataLen);
    }

    public int getTag()
    {
        return this.tag;
    }

    public int getPacketType()
    {
        return this.packetType;
    }

    public int getDNSPort()
    {
        return this.dnsPort;
    }

    public InetAddress getDNSAddress()
    {
        return this.dnsAddress;
    }

    public long getPingSendTime()
    {
        return this.pingSendTime;
    }

    public String getPacketTypeString()
    {
    	String type = null;
    	switch(this.packetType)
    	{
    	    case BN_PACKET_HTTP:
    	        type = "HTTP";
    	        break;
    	    case BN_PACKET_HTTPS:
        		type = "HTTPS";
        		break;
    	    case BN_PACKET_DNS:
        		type = "DNS";
        		break;
            case BN_PACKET_PING:
                type = "PING";
                break;
            case BN_PACKET_PONG:
                type = "PONG";
                break;
    	    default:
    	        type = "UNKNOWN";
    	        break;
	    }
    	return type;
    }

    public byte[] getData()
    {
        return this.data;
    }

    public int getDataLength()
    {
        return this.len;
    }

    public boolean isPooled()
    {
        return this.isPooled;
    }

    public boolean shouldClose()
    {
        return this.shouldClose;
    }

    public void setTag(int tag)
    {
        this.tag = tag;
    }

    public void setPacketType(int packetType)
    {
    	this.packetType = packetType;
    }

    public void setData(byte[] data, int len)
    {
        if ((data == null) || (len == 0))
        {
            this.len = -1;
        }
        else
        {
            this.setData(data, 0, len);
        }
    }

    public void setData(byte[] data, int offset, int len)
    {
    	this.len = len;
    	System.arraycopy(data, offset, this.data, 0, len);
    }

    public void setPooled(boolean isPooled)
    {
        this.isPooled = isPooled;
    }

    public void setClose(boolean shouldClose)
    {
        this.shouldClose = shouldClose;
    }

    public void setDNSPort(int dnsPort)
    {
        this.dnsPort = dnsPort;
    }

    public void setDNSAddress(InetAddress dnsAddress)
    {
        this.dnsAddress = dnsAddress;
    }

    public void setPingTime()
    {
        this.pingSendTime = System.currentTimeMillis();
    }

    public String toString()
    {
    	StringBuffer str = new StringBuffer("BlueNetPacket[");
        str.append("Type=");
        str.append(this.getPacketTypeString());
        str.append(", Tag=");
        str.append(this.tag);
    	if (this.packetType == BN_PACKET_DNS)
    	{
    	    str.append(", InetAddress=");
    	    str.append(this.dnsAddress.toString());
    	    str.append(", Port=");
    	    str.append(this.dnsPort);
    	}
    	else if ((this.packetType == BN_PACKET_PING) ||
    	         (this.packetType == BN_PACKET_PONG))
        {
    	    str.append(", PingSendTime=");
    	    str.append(this.pingSendTime);
        }
    	else
    	{
        	str.append(", Data Length=");
        	str.append(this.len);
            str.append(", ShouldClose=");
            str.append(this.shouldClose);
            str.append(", isPooled=");
            str.append(this.isPooled);
        }
        str.append(", CheckSum=");
        str.append(this.getCheckSum());
    	str.append("]\n");
    	return str.toString();
    }

    public long getCheckSum()
    {
//        if ((this.checksum == 0) && (this.len > 0))
//        {
//            if (adChecksum == null)
//            {
//                adChecksum = new Adler32();
//            }
//            adChecksum.update(this.data, 0, this.len);
//            this.checksum = adChecksum.getValue();
//        }
        // FOR TESTING, FORCE RECOMPUTE EVERY SINGLE TIME
        if ((this.len == -1) || (this.len == MAX_DATA_LEN))
        {
            this.checksum = 0;
        }
        else
        {
            adChecksum = new Adler32();
            adChecksum.update(this.data, 0, this.len);
            this.checksum = adChecksum.getValue();
        }
        return this.checksum;
    }
}
