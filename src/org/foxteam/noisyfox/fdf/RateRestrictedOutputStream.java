package org.foxteam.noisyfox.fdf;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-24
 * Time: 下午7:15
 * To change this template use File | Settings | File Templates.
 */
public class RateRestrictedOutputStream extends OutputStream {

    private RateRestriction mRateRestriction;
    private long mRateMax;
    private OutputStream mOutputStream;
    private static long processedDataByte = 0;

    public RateRestrictedOutputStream(OutputStream outputStream, RateRestriction rateRestriction, long rateMax) {
        if (outputStream == null || rateRestriction == null || rateMax <= 0) {
            throw new IllegalArgumentException();
        }

        mOutputStream = outputStream;
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
    public void write(int b) throws IOException {
        if (processedDataByte > 1024 * 50) {
            processedDataByte = 0;
            checkRate();
        }

        mOutputStream.write(b);
        mRateRestriction.userFileTransferTotalBytes++;
        processedDataByte++;
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException {
        if (processedDataByte > 1024 * 50) {
            processedDataByte = 0;
            checkRate();
        }

        mOutputStream.write(b, off, len);
        mRateRestriction.userFileTransferTotalBytes += len;
        processedDataByte += len;
    }

    @Override
    public void flush() throws IOException {
        mOutputStream.flush();
    }

    @Override
    public void close() throws IOException {
        mOutputStream.close();
    }
}
