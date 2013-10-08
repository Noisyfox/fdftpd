package org.foxteam.noisyfox.fdf.Host;

import org.foxteam.noisyfox.fdf.FtpCodes;
import org.foxteam.noisyfox.fdf.FtpUtil;
import org.foxteam.noisyfox.fdf.NodeRespond;
import org.foxteam.noisyfox.fdf.Path;

import java.io.*;
import java.net.Socket;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-27
 * Time: 下午12:53
 * To change this template use File | Settings | File Templates.
 */
public class HostNodeSession extends Thread {
    private final Object mWaitObj = new Object();
    private final Socket mSocket;
    private final PrintWriter mWriter;
    private final BufferedReader mReader;
    private final NodeRespond mNodeRespond = new NodeRespond();
    private static final long CALL_TIMEOUT = 100 * 1000;

    private HostServant mHostServant;
    private long mCmdCallTimeStamp = 0L;
    private boolean mIsAlive = false;
    private boolean mIsKilled = false;

    public HostNodeSession(Socket socket) throws IOException {
        mSocket = socket;
        try {
            mReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            mWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            throw new IOException(e);
        }
    }

    private boolean readStatus(boolean longWait) {
        return FtpUtil.readNodeRespond(mSocket, mReader, mNodeRespond, longWait ? 0 : 10000);
    }

    private boolean readAndCheckStatus(boolean longWait, int nodeRespond, int nodeStatus) {
        return readStatus(longWait)
                && (nodeRespond == FtpCodes.ANYCODE || mNodeRespond.mRespondCode == nodeRespond)
                && (nodeStatus == FtpCodes.ANYCODE || mNodeRespond.mStatus.mStatusCode == nodeStatus);
    }

    /**
     * 连接准备，主机和node进行必要的信息交换，并启动session至可使用状态
     *
     * @param hostServant
     */
    public boolean prepareConnection(HostServant hostServant) {
        mHostServant = hostServant;
        //告知登录用户名
        FtpUtil.ftpWriteStringRaw(mWriter, "UNAME " + mHostServant.mSession.user);
        if (!readAndCheckStatus(false, FtpCodes.NODE_OPSOK, FtpCodes.HOST_INFOOK)) {
            System.out.println("Error exchanging information.");
            return false;
        }
        //告知客户端地址
        FtpUtil.ftpWriteStringRaw(mWriter, "RADDR " + mHostServant.mSession.userRemoteAddr);
        if (!readAndCheckStatus(false, FtpCodes.NODE_OPSOK, FtpCodes.HOST_INFOOK)) {
            System.out.println("Error exchanging information.");
            return false;
        }

        this.start();

        return true;
    }

    @Override
    public void run() {
        System.out.println("Node session created!");
        synchronized (mWaitObj) {//这个线程用来发送心跳包
            mIsAlive = true;
            mCmdCallTimeStamp = System.currentTimeMillis();
            while (mIsAlive && !mIsKilled) {
                FtpUtil.ftpWriteStringRaw(mWriter, "NOOP");//发送心跳
                if (!readAndCheckStatus(false, FtpCodes.NODE_OPSOK, FtpCodes.FTP_NOOPOK)) { //挂了
                    break;
                }
                // 检测该session是否空闲太久
                long currentTime = System.currentTimeMillis();
                if (currentTime - mCmdCallTimeStamp > CALL_TIMEOUT) {//太久
                    kill();
                    break;
                }
                try {
                    mWaitObj.wait(10000);//等待时释放锁，使该session可以处理外界请求
                    //结束等待时会尝试获取锁，此时如果外界请求没有结束，则会阻塞，否则开始发送心跳
                } catch (InterruptedException ignored) {
                }
            }
            clean();
        }
    }

    private void clean() {
        try {
            mSocket.close();
        } catch (IOException ignored) {
        }
    }

    public void kill() {
        synchronized (mWaitObj) {
            if (!mIsAlive) return;
            int tryCount = 0;
            FtpUtil.ftpWriteStringRaw(mWriter, "QUIT");

            while (!readAndCheckStatus(false, FtpCodes.NODE_OPSOK, FtpCodes.FTP_GOODBYE)) {
                if (tryCount < 4) {
                    System.out.println("Error killing node session. Try again.");
                    tryCount++;
                    FtpUtil.ftpWriteStringRaw(mWriter, "QUIT");
                } else {
                    System.out.println("Error killing node session. Give up.");
                    break;
                }
            }
            mIsKilled = true;
            mIsAlive = false;
        }
    }

    public boolean isSessionAlive() {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();
            return mIsAlive && !mIsKilled;
        }
    }

    public void handleCwd(Path path) {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();
            FtpUtil.ftpWriteStringRaw(mWriter, "CWD " + path.getAbsolutePath());
            if (readAndCheckStatus(false, FtpCodes.NODE_OPSOK, FtpCodes.FTP_CWDOK)) {
                mHostServant.mSession.userCurrentDir = path;
            }
            if (mNodeRespond.mRespondCode != FtpCodes.NODE_OPSOK) {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to change directory.");
            } else {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, mNodeRespond.mStatus.mStatusCode, ' ', mNodeRespond.mStatus.mStatusMsg);
            }
        }
    }

    public void handleSize(Path path) {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();
            FtpUtil.ftpWriteStringRaw(mWriter, "SIZE " + path.getAbsolutePath());
            if (!readStatus(false) || mNodeRespond.mRespondCode != FtpCodes.NODE_OPSOK) {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, FtpCodes.FTP_FILEFAIL, ' ', "Could not get file size.");
            } else {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, mNodeRespond.mStatus.mStatusCode, ' ', mNodeRespond.mStatus.mStatusMsg);
            }
        }
    }

}
