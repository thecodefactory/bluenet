package com.nm.bluenet;

import android.util.Log;

import com.nm.bluenetcommon.BlueNetLog;

public class BlueNetLogger implements BlueNetLog
{
    @Override
    public void d(String tag, String msg)
    {
        Log.d(tag, msg);
    }

    @Override
    public void e(String tag, String msg)
    {
        Log.e(tag, msg);
    }

    @Override
    public void i(String tag, String msg)
    {
        Log.i(tag, msg);
    }
}
