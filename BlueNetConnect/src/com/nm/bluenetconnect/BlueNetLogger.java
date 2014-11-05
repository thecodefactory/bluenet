package com.nm.bluenetconnect;

import java.util.logging.FileHandler;
import java.util.logging.Logger;

import com.nm.bluenetcommon.BlueNetLog;

public class BlueNetLogger implements BlueNetLog
{
    private Logger logger = Logger.getLogger("BlueNetConnect");

    @Override
    public void d(String tag, String msg)
    {
        this.logger.info(tag + " " + msg);
    }

    @Override
    public void e(String tag, String msg)
    {
        this.logger.severe(tag + " " + msg);
    }

    @Override
    public void i(String tag, String msg)
    {
        this.logger.info(tag + " " + msg);
    }

    public void addHandler(FileHandler fileLogger)
    {
        this.logger.addHandler(fileLogger);
    }
}
