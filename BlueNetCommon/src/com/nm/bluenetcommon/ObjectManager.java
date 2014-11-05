package com.nm.bluenetcommon;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class ObjectManager
{
    public final static int INVALID_TAG = Integer.MIN_VALUE;

    private int index = 0;
	private Map<Integer, Object> objectTagMap = null;

	public ObjectManager()
	{
		this.objectTagMap = new HashMap<Integer, Object>();
	}

	// useful for server side when packet already has a tag assigned.
	// if trackIncrement is true, we're careful to make sure that if
	// the tag being added is greater than the internal index, we
	// adjust the internal index so the next call to 'addObject' will
	// return a sensical value
	synchronized public void addEntry(int tag, Object obj, boolean trackIncrement)
	{
	    if ((trackIncrement == true) && (java.lang.Math.abs(tag) > this.index))
	    {
	        // no need to '+1' here, as it's incremented in addObject
	        this.index = java.lang.Math.abs(tag);
	    }
		this.objectTagMap.put(new Integer(tag), obj);
	}

	// returns the tag of the newly added socket, or the tag of the socket if it already existed.
	// useful for client side when packet does not already have a tag assigned
	synchronized public int addObject(Object obj)
	{
		int ret = -1;
		Integer foundKey = null;
		for (Entry<Integer, Object> cur : this.objectTagMap.entrySet())
		{
			if (cur.getValue() == obj)
			{
				foundKey = cur.getKey();
				break;
			}
		}
		if (foundKey != null)
		{
			ret = foundKey.intValue();
		}
		else
		{
			this.index++;

			ret = this.index;
			this.objectTagMap.put(new Integer(ret), obj);
		}
		return ret;
	}

	synchronized public int getNumObjects()
	{
	    return this.objectTagMap.size();
	}

	synchronized public Object getObject(int tag)
	{
		return this.objectTagMap.get(new Integer(tag));
	}

    synchronized public int getTag(Object obj)
    {
        for (Entry<Integer, Object> cur : this.objectTagMap.entrySet())
        {
            if (cur.getValue() == obj)
            {
                return cur.getKey().intValue();
            }
        }
        return ObjectManager.INVALID_TAG;
    }

    synchronized public void removeObject(Object obj)
	{
		Integer foundKey = null;
		for (Entry<Integer, Object> cur : this.objectTagMap.entrySet())
		{
			if (cur.getValue() == obj)
			{
				foundKey = cur.getKey();
				break;
			}
		}
		if (foundKey != null)
		{
			this.objectTagMap.remove(foundKey);
		}
	}

    synchronized public void removeAllObjects()
    {
        this.objectTagMap.clear();
    }
}
