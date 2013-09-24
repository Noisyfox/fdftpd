package org.foxteam.noisyfox.fdf;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-24
 * Time: 下午7:15
 * To change this template use File | Settings | File Templates.
 */
public class RateRestrictedInputStream extends InputStream {

    private RateRestriction mRateRestriction;
    private long mRateMax;
    private InputStream mInputStream;
    private static long processedDataByte = 0;

    public RateRestrictedInputStream(InputStream inputStream, RateRestriction rateRestriction, long rateMax) {
        if (inputStream == null || rateRestriction == null || rateMax <= 0) {
            throw new IllegalArgumentException();
        }

        mInputStream = inputStream;
        mRateRestriction = rateRestriction;
        mRateMax = rateMax;
    }

    private final void checkRate() {
        long currentTime = System.currentTimeMillis();
        long dTimeStart = currentTime - mRateRestriction.userFileTransferStartTime;
        long dTimeLast = currentTime - mRateRestriction.userFileTransferLastTime;

        if (dTimeLast > 1000) {//离最后一次检测时间过久
            mRateRestriction.userFileTransferTotalBytes = 0;
            mRateRestriction.userFileTransferStartTime = currentTime;
        } else {
            //计算如果使用最大速度，那么已经传输的字节需要用多少毫秒
            long needTime = (long) (mRateRestriction.userFileTransferTotalBytes / (mRateMax / 1000.0));
            long sleepTime = needTime - dTimeStart;
            if (sleepTime > 0) {
                if (sleepTime > 1000) {
                    sleepTime = 1000;
                }
                try {
                    Thread.sleep(sleepTime);
                    currentTime += sleepTime;
                } catch (InterruptedException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
        }

        mRateRestriction.userFileTransferLastTime = currentTime;
    }

    @Override
    public int read() throws IOException {
        if (processedDataByte > 1024 * 50) {
            processedDataByte = 0;
            checkRate();
        }

        int data = mInputStream.read();
        if (data != -1) {
            mRateRestriction.userFileTransferTotalBytes++;
            processedDataByte++;
        }
        return data;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (processedDataByte > 1024 * 50) {
            processedDataByte = 0;
            checkRate();
        }

        int size = mInputStream.read(b, off, len);
        if (size != -1) {
            mRateRestriction.userFileTransferTotalBytes += size;
            processedDataByte += size;
        }
        return size;
    }

    @Override
    public long skip(long n) throws IOException {
        return mInputStream.skip(n);
    }

    @Override
    public int available() throws IOException {
        return mInputStream.available();
    }

    @Override
    public void close() throws IOException {
        mInputStream.close();
    }

    @Override
    public synchronized void mark(int readlimit) {
        mInputStream.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        mInputStream.reset();
    }

    @Override
    public boolean markSupported() {
        return mInputStream.markSupported();
    }
}
