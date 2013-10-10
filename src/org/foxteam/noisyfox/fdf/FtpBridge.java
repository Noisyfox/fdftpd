package org.foxteam.noisyfox.fdf;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-10-9
 * Time: 下午4:14
 * ftp节点之间互相传输数据时的桥，
 * 用在客户端连接了错误的节点时从其它节点转移数据时用，
 * 始终使用字节流传输数据。
 */
public class FtpBridge extends Thread {
    private static final int BUFFER_MAX_SIZE = 1024 * 10;

    private final Object mWaitObj = new Object();
    private final ServerSocket mSocketServer;
    private final String mNodeAddress;
    private final OnBridgeWorkFinishListener mFinishListener;

    private volatile boolean isStarted = false;
    private volatile boolean isKilled = false;
    private boolean isReceive = false;
    private Socket mSocket;
    private BufferedInputStream mInputeStream;
    private BufferedOutputStream mOutputStream;

    public FtpBridge(ServerSocket socketListener, String nodeAddress, OnBridgeWorkFinishListener listener) {
        mSocketServer = socketListener;
        mNodeAddress = nodeAddress;
        mFinishListener = listener;
    }

    public synchronized void startForReceiving(BufferedOutputStream dstStream) {
        if (isStarted) {
            return;
        }
        isReceive = true;
        isStarted = true;
        mOutputStream = dstStream;
        this.start();
    }

    public synchronized void startForSending(BufferedInputStream resStream) {
        if (isStarted) {
            return;
        }
        isReceive = false;
        isStarted = true;
        mInputeStream = resStream;
        this.start();
    }

    @Override
    public void run() {
        //开始监听
        try {
            mSocket = mSocketServer.accept();
        } catch (IOException e) {
            return;
        } finally {
            try {
                mSocketServer.close();
            } catch (IOException ignored) {
            }
        }
        //检查是否是来自指定节点的连接
        String clientAddr = FtpUtil.getSocketRemoteAddress(mSocket);
        if (!mNodeAddress.equals(clientAddr)) {
            return;
        }

        if (isReceive) {
            try {
                mInputeStream = new BufferedInputStream(mSocket.getInputStream());
            } catch (IOException e) {
                try {
                    mSocket.close();
                } catch (IOException ignored) {
                }
                return;
            }
        } else {
            try {
                mOutputStream = new BufferedOutputStream(mSocket.getOutputStream());
            } catch (IOException e) {
                try {
                    mSocket.close();
                } catch (IOException ignored) {
                }
                return;
            }
        }

        //开始进行传输
        byte[] buffer = new byte[BUFFER_MAX_SIZE];
        int readSize;
        try {
            while (!isKilled && (readSize = mInputeStream.read(buffer)) != -1) {
                mOutputStream.write(buffer, 0, readSize);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            mOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            mSocket.close();
        } catch (IOException ignored) {
        }

        if (mFinishListener != null) {
            mFinishListener.onWorkFinish();
        }

        synchronized (mWaitObj) {
            isKilled = true;
            mWaitObj.notify();
        }
    }

    public void kill() {
        synchronized (mWaitObj) {
            if (isKilled) {
                return;
            }
            isKilled = true;
            try {
                mWaitObj.wait();
            } catch (InterruptedException ignored) {
            }
        }
    }

    public boolean isDead() {
        return isKilled;
    }

    public static interface OnBridgeWorkFinishListener {
        void onWorkFinish();
    }
}