package com.nm.bluenetcommon;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class HTTPSTagManager
{
    // maps the HTTP tag to the secure HTTPS tag (respectively)
    private Map<Integer, Integer> httpsTagMap = null;

    public HTTPSTagManager()
    {
        this.httpsTagMap = new HashMap<Integer, Integer>();
    }

    public void addEntry(int httpTag, int httpsTag)
    {
        this.httpsTagMap.put(new Integer(httpTag), new Integer(httpsTag));
    }

    public Integer getHTTPTagFromHTTPSTag(int httpsTag)
    {
        Integer httpTag = null;
        for(Entry<Integer, Integer> cur : this.httpsTagMap.entrySet())
        {
            if (cur.getValue().intValue() == httpsTag)
            {
                httpTag = cur.getKey();
                break;
            }
        }
        return httpTag;
    }

    public Integer getHTTPSTagFromHTTPTag(int httpTag)
    {
        return this.httpsTagMap.get(new Integer(httpTag));
    }

    public void removeEntry(int httpTag)
    {
        this.httpsTagMap.remove(httpTag);
    }

    public void removeAllEntries()
    {
        this.httpsTagMap.clear();
    }

    public int getNumHTTPSConnections()
    {
        return this.httpsTagMap.size();
    }
}
