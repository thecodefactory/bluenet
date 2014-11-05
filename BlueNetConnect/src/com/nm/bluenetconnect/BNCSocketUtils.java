package com.nm.bluenetconnect;

import java.net.Socket;

import java.io.InputStream;
import java.io.OutputStream;

public class BNCSocketUtils
{
    public static final int MAX_BUF_LEN = 32768;
    public static final int PACKET_BUF_LEN = (MAX_BUF_LEN + 16);
    private static final int HAVE_DATA_WAIT_RETRIES = 3;

    /*
     * For all reads, if timeoutMS is 0, do blocking reads.
     * If timeoutMS is -1, only read if data is immediately available.
     * Otherwise, poll the socket for up to timeout milliseconds until
     * data becomes available, or until the timeout expires.
     */
    public static String readSCSocket(InputStream is, int timeoutMS) throws Exception
    {
        byte[] buffer = new byte[MAX_BUF_LEN];
        String ret = null;
        if (readSCSocketBytes(is, timeoutMS, buffer, MAX_BUF_LEN) > 0)
        {
            ret = new String(buffer);
        }
        return ret;
    }

    public static int readSCSocketBytes(
        InputStream is, int timeoutMS, byte[] buffer, int maxLen) throws Exception
    {
        int ret = 0, timeout = -2, bytesAvailable = 0, curRead = 0, waitCount = 0;
        boolean haveData = false;

        while(timeout < timeoutMS)
        {
            bytesAvailable = is.available();
            if (bytesAvailable > 0)
            {
                haveData = true;
                waitCount = 0;
                curRead = ((bytesAvailable > (maxLen - ret)) ? (maxLen - ret) : bytesAvailable);
                curRead = is.read(buffer, ret, curRead);
                ret += curRead;
                if (ret >= maxLen)
                {
                    return ret;
                }
            }
            else
            {
                // if timeoutMS is -1 and no data is available,
                // just return immediately
                if (timeoutMS == -1)
                {
                    break;
                }

                Thread.sleep(100);

                // never increment when timeoutMS is 0
                // (i.e. block forever until data arrives)
                if (timeoutMS != 0)
                {
                    timeout += 100;
                }
                else if ((haveData == true) && (waitCount++ > HAVE_DATA_WAIT_RETRIES))
                {
                    // if we're sitting on data and no more is available,
                    // give up after HAVE_DATA_WAIT_RETRIES retries and break out
                    break;
                }
            }
        }
        return ret;
    }

    public static int readSCSocketBytesFully(
        InputStream is, byte[] buffer, int len) throws Exception
    {
        int ret = 0, curRead = 0;

        while(true)
        {
            curRead = (len - ret);
            curRead = is.read(buffer, ret, curRead);
            ret += curRead;
            if (ret >= len)
            {
                break;
            }
            Thread.sleep(20);
        }
        return ret;
    }

    public static String readNetSocket(Socket socket, int timeoutMS) throws Exception
    {
        byte[] buffer = new byte[MAX_BUF_LEN];
        String ret = null;
        if (readNetSocketBytes(socket, timeoutMS, buffer, MAX_BUF_LEN) > 0)
        {
            ret = new String(buffer);
        }
        return ret;
    }

    public static int readNetSocketBytes(Socket socket, int timeoutMS, byte[] buffer, int maxLen)
        throws Exception
    {
        int ret = 0, timeout = -2, bytesAvailable = 0, curRead = 0, waitCount = 0;
        boolean haveData = false;

        InputStream is = socket.getInputStream();
        while(timeout < timeoutMS)
        {
            bytesAvailable = is.available();
            if (bytesAvailable > 0)
            {
                haveData = true;
                waitCount = 0;
                curRead = ((bytesAvailable > (maxLen - ret)) ? (maxLen - ret) : bytesAvailable);
                curRead = is.read(buffer, ret, curRead);
                ret += curRead;
                if (ret >= maxLen)
                {
                    return ret;
                }
            }
            else
            {
                // if timeoutMS is -1 and no data is available,
                // just return immediately
                if (timeoutMS == -1)
                {
                    break;
                }

                Thread.sleep(10);

                // never increment when timeoutMS is 0
                // (i.e. block forever until data arrives)
                if (timeoutMS != 0)
                {
                    timeout += 10;
                }
                else if ((haveData == true) && (waitCount++ > HAVE_DATA_WAIT_RETRIES))
                {
                    // if we're sitting on data and no more is available,
                    // give up after HAVE_DATA_WAIT_RETRIES retries and break out
                    break;
                }
            }
        }
        return ret;
    }

    public static void writeSCSocket(OutputStream os, String data) throws Exception
    {
        writeSCSocketBytes(os, data.getBytes(), data.length());
    }

    public static void writeSCSocketBytes(OutputStream os, byte[] buffer, int len) throws Exception
    {
        os.write(buffer, 0, len);
        os.flush();
    }

    public static void writeNetSocket(Socket socket, String data, int timeoutMS) throws Exception
    {
        writeNetSocketBytes(socket, data.getBytes(), data.length());
    }

    public static void writeNetSocketBytes(Socket socket, byte[] buffer, int len) throws Exception
    {
        OutputStream os = socket.getOutputStream();
        os.write(buffer, 0, len);
        os.flush();
    }
}
