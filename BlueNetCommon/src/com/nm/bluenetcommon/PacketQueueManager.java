package com.nm.bluenetcommon;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class PacketQueueManager
{
    private Map<Integer, LinkedList<BlueNetPacket>> pendingWriteMap = null;

    public PacketQueueManager()
    {
        this.pendingWriteMap = new HashMap<Integer, LinkedList<BlueNetPacket>>();
    }

    // ignores tag inside packet, and adds this to the appropriate writeList.
    // special method for use with HTTPS (where tag re-writes are needed at times).
    synchronized public void addPacketToWriteListHead(int tag, BlueNetPacket curPacket)
    {
        Integer iTag = new Integer(tag);
        LinkedList<BlueNetPacket> writeList = this.pendingWriteMap.get(iTag);
        if (writeList == null)
        {
            writeList = new LinkedList<BlueNetPacket>();
            this.pendingWriteMap.put(tag, writeList);
        }
        writeList.addFirst(curPacket);
    }

    // ignores tag inside packet, and adds this to the appropriate writeList.
    // special method for use with HTTPS (where tag re-writes are needed at times).
    synchronized public void addPacketToWriteListTail(int tag, BlueNetPacket curPacket)
    {
        LinkedList<BlueNetPacket> writeList = this.pendingWriteMap.get(tag);
        if (writeList == null)
        {
            writeList = new LinkedList<BlueNetPacket>();
            this.pendingWriteMap.put(new Integer(tag), writeList);
        }
        writeList.addLast(curPacket);
    }

    synchronized public BlueNetPacket getNextPacketOnWriteList(int tag)
    {
        BlueNetPacket curPacket = null;
        Integer iTag = new Integer(tag);
        LinkedList<BlueNetPacket> writeList = this.pendingWriteMap.get(iTag);
        if ((writeList != null) && (writeList.size() > 0))
        {
            curPacket = writeList.removeFirst();
        }
        return curPacket;
    }

    synchronized public int getPacketWriteListSize(int tag)
    {
        int ret = -1;
        Integer iTag = new Integer(tag);
        LinkedList<BlueNetPacket> writeList = this.pendingWriteMap.get(iTag);
        if (writeList != null)
        {
            ret = writeList.size();
        }
        return ret;
    }

    synchronized public void removeWriteList(int tag)
    {
        Integer iTag = new Integer(tag);
        LinkedList<BlueNetPacket> writeList = this.pendingWriteMap.get(iTag);
        if (writeList != null)
        {
            writeList.clear();
        }
        this.pendingWriteMap.remove(iTag);
    }

    synchronized public void removeAllWriteLists()
    {
        for (Map.Entry<Integer, LinkedList<BlueNetPacket>> cur : this.pendingWriteMap.entrySet())
        {
            LinkedList<BlueNetPacket> writeList = cur.getValue();
            if (writeList != null)
            {
                writeList.clear();
            }
        }
        this.pendingWriteMap.clear();
    }
}
