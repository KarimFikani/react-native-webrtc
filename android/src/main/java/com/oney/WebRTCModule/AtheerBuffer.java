package com.oney.WebRTCModule;

public final class AtheerBuffer {

    public static byte[] buffer0;
    public static byte[] buffer1;

    private static int height;
    private static int width;
    private static int writeBuffer = 0;
    private static int readBuffer = 1;

    public static synchronized void commitWriteBuffer() {
        if (writeBuffer == 0) {
            writeBuffer = 1;
            readBuffer = 0;
        } else {
            writeBuffer = 0;
            readBuffer = 1;
        }
    }

    public static synchronized int getWriteBuffer() {
        return writeBuffer;
    }

    public static synchronized int getReadBuffer() {
        return readBuffer;
    }

    public static synchronized int getHeight() {
        return height;
    }

    public static synchronized int getWidth() {
        return width;
    }

    public static synchronized void setHeight(int h) {
        height = h;
    }

    public static synchronized void setWidth(int w) {
        width = w;
    }
}
