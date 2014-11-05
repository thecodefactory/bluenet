package com.nm.bluenetcommon;

public class DataUtils
{
    public static boolean isGetRequest(byte[] srcBuffer)
    {
        return ((srcBuffer[0] == 0x47) && (srcBuffer[1] == 0x45) &&
                (srcBuffer[2] == 0x54) && (srcBuffer[3] == 0x20));
    }

    public static boolean isPutRequest(byte[] srcBuffer)
    {
        return ((srcBuffer[0] == 0x50) && (srcBuffer[1] == 0x55) &&
                (srcBuffer[2] == 0x54) && (srcBuffer[3] == 0x20));
    }

    public static boolean isPostRequest(byte[] srcBuffer)
    {
        return ((srcBuffer[0] == 0x50) && (srcBuffer[1] == 0x4F) &&
                (srcBuffer[2] == 0x53) && (srcBuffer[3] == 0x54) &&
                (srcBuffer[4] == 0x20));
    }

    public static boolean isHeadRequest(byte[] srcBuffer)
    {
        return ((srcBuffer[0] == 0x48) && (srcBuffer[1] == 0x45) &&
                (srcBuffer[2] == 0x41) && (srcBuffer[3] == 0x44) &&
                (srcBuffer[4] == 0x20));
    }

    public static boolean isTraceRequest(byte[] srcBuffer)
    {
        return ((srcBuffer[0] == 0x54) && (srcBuffer[1] == 0x52) &&
                (srcBuffer[2] == 0x41) && (srcBuffer[3] == 0x43) &&
                (srcBuffer[4] == 0x45) && (srcBuffer[5] == 0x20));
    }

    public static boolean isPatchRequest(byte[] srcBuffer)
    {
        return ((srcBuffer[0] == 0x50) && (srcBuffer[1] == 0x41) &&
                (srcBuffer[2] == 0x54) && (srcBuffer[3] == 0x43) &&
                (srcBuffer[4] == 0x48) && (srcBuffer[5] == 0x20));
    }

    public static boolean isDeleteRequest(byte[] srcBuffer)
    {
        return ((srcBuffer[0] == 0x44) && (srcBuffer[1] == 0x45) &&
                (srcBuffer[2] == 0x4C) && (srcBuffer[3] == 0x45) &&
                (srcBuffer[4] == 0x54) && (srcBuffer[5] == 0x45) &&
                (srcBuffer[6] == 0x20));
    }

    public static boolean isOptionsRequest(byte[] srcBuffer)
    {
        return ((srcBuffer[0] == 0x4F) && (srcBuffer[1] == 0x50) &&
                (srcBuffer[2] == 0x54) && (srcBuffer[3] == 0x49) &&
                (srcBuffer[4] == 0x4F) && (srcBuffer[5] == 0x4E) &&
                (srcBuffer[6] == 0x53) && (srcBuffer[7] == 0x20));
    }

    public static boolean isConnectRequest(byte[] srcBuffer)
    {
        return ((srcBuffer[0] == 0x43) && (srcBuffer[1] == 0x4F) &&
                (srcBuffer[2] == 0x4E) && (srcBuffer[3] == 0x4E) &&
                (srcBuffer[4] == 0x45) && (srcBuffer[5] == 0x43) &&
                (srcBuffer[6] == 0x54) && (srcBuffer[7] == 0x20));
    }

    public static boolean isHTTPResponse(byte[] srcBuffer)
    {
        return ((srcBuffer[0] == 0x48) && (srcBuffer[1] == 0x54) &&
                (srcBuffer[2] == 0x54) && (srcBuffer[3] == 0x50));
    }    
}
