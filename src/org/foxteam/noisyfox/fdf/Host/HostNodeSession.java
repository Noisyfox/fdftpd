package org.foxteam.noisyfox.fdf.Host;

import org.foxteam.noisyfox.fdf.*;

import java.io.*;
import java.net.Socket;
import java.util.LinkedList;

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
    private final HostNodeConnector mConnector;
    private static final long CALL_TIMEOUT = 100 * 1000;

    private HostServant mHostServant;
    private long mCmdCallTimeStamp = 0L;
    private boolean mIsAlive = false;
    private boolean mIsKilled = false;
    public int mBridgePort = -1;

    public HostNodeSession(HostNodeConnector connector, Socket socket) throws IOException {
        mConnector = connector;
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

    public String getNodeAddress() {
        return mConnector.mHostNodeDefinition.adderss;
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
        //告知是否是匿名用户
        if (mHostServant.mSession.userAnon) {
            FtpUtil.ftpWriteStringRaw(mWriter, "ANON");
            if (!readAndCheckStatus(false, FtpCodes.NODE_OPSOK, FtpCodes.HOST_INFOOK)) {
                System.out.println("Error exchanging information.");
                return false;
            }
        }
        //告知客户端地址
        FtpUtil.ftpWriteStringRaw(mWriter, "RADDR " + mHostServant.mSession.userRemoteAddr);
        if (!readAndCheckStatus(false, FtpCodes.NODE_OPSOK, FtpCodes.HOST_INFOOK)) {
            System.out.println("Error exchanging information.");
            return false;
        }
        if (!mHostServant.mSession.userAnon) {
            //传输permission设置
            LinkedList<Pair<Path, Integer>> dirPair = mHostServant.mSession.permission.getPermissionPairDir();
            for (Pair<Path, Integer> p : dirPair) {
                FtpUtil.ftpWriteStringRaw(mWriter, "PERD " + p.getValue1().getAbsolutePath() + "::" + p.getValue2());
                if (!readAndCheckStatus(false, FtpCodes.NODE_OPSOK, FtpCodes.HOST_INFOOK)) {
                    System.out.println("Error exchanging information.");
                    return false;
                }
            }
            LinkedList<Pair<Path, Integer>> filePair = mHostServant.mSession.permission.getPermissionPairFile();
            for (Pair<Path, Integer> p : filePair) {
                FtpUtil.ftpWriteStringRaw(mWriter, "PERF " + p.getValue1().getAbsolutePath() + "::" + p.getValue2());
                if (!readAndCheckStatus(false, FtpCodes.NODE_OPSOK, FtpCodes.HOST_INFOOK)) {
                    System.out.println("Error exchanging information.");
                    return false;
                }
            }
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
        mIsAlive = false;
        System.out.println("Node session died!");
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
                mHostServant.mSession.userCurrentDirNode = mConnector.mHostNodeDefinition.number;
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

    public long doSize(Path path) {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();

            FtpUtil.ftpWriteStringRaw(mWriter, "SIZE " + path.getAbsolutePath());
            if (readAndCheckStatus(false, FtpCodes.NODE_OPSOK, FtpCodes.FTP_SIZEOK)) {
                try {
                    return Long.parseLong(mNodeRespond.mStatus.mStatusMsg);
                } catch (NumberFormatException ex) {
                    return -1;
                }
            } else {
                return -1;
            }
        }
    }

    public void handleMdtm(Path path, long mTime) {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();

            FtpUtil.ftpWriteStringRaw(mWriter, "MDTM " + path.getAbsolutePath() + "::" + mTime);
            if (!readStatus(false) || mNodeRespond.mRespondCode != FtpCodes.NODE_OPSOK) {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, FtpCodes.FTP_FILEFAIL, ' ',
                        mTime >= 0 ? "Could not set file modification time." : "Could not get file modification time.");
            } else {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, mNodeRespond.mStatus.mStatusCode, ' ', mNodeRespond.mStatus.mStatusMsg);
            }
        }
    }

    public void handleMkd(Path path) {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();

            FtpUtil.ftpWriteStringRaw(mWriter, "MKD " + path.getAbsolutePath());
            if (!readStatus(false) || mNodeRespond.mRespondCode != FtpCodes.NODE_OPSOK) {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, FtpCodes.FTP_FILEFAIL, ' ', "Create directory operation failed.");
            } else {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, mNodeRespond.mStatus.mStatusCode, ' ', mNodeRespond.mStatus.mStatusMsg);
            }
        }
    }

    public void handleRmd(Path path) {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();

            FtpUtil.ftpWriteStringRaw(mWriter, "RMD " + path.getAbsolutePath());
            if (!readStatus(false) || mNodeRespond.mRespondCode != FtpCodes.NODE_OPSOK) {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, FtpCodes.FTP_FILEFAIL, ' ', "Remove directory operation failed.");
            } else {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, mNodeRespond.mStatus.mStatusCode, ' ', mNodeRespond.mStatus.mStatusMsg);
            }
        }
    }

    public void handleDele(Path path) {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();

            FtpUtil.ftpWriteStringRaw(mWriter, "DELE " + path.getAbsolutePath());
            if (!readStatus(false) || mNodeRespond.mRespondCode != FtpCodes.NODE_OPSOK) {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, FtpCodes.FTP_FILEFAIL, ' ', "Delete operation failed.");
            } else {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, mNodeRespond.mStatus.mStatusCode, ' ', mNodeRespond.mStatus.mStatusMsg);
            }
        }
    }

    public void handleRnfr(Path path) {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();

            FtpUtil.ftpWriteStringRaw(mWriter, "RNFR " + path.getAbsolutePath());
            if (!readStatus(false) || mNodeRespond.mRespondCode != FtpCodes.NODE_OPSOK) {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, FtpCodes.FTP_FILEFAIL, ' ', "RNFR command failed.");
            } else {
                if (mNodeRespond.mStatus.mStatusCode == FtpCodes.FTP_RNFROK) {
                    mHostServant.mSession.userRnfrFileNode = mConnector.mHostNodeDefinition.number;
                }
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, mNodeRespond.mStatus.mStatusCode, ' ', mNodeRespond.mStatus.mStatusMsg);
            }
        }
    }

    public void handleRnto(Path path) {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();

            FtpUtil.ftpWriteStringRaw(mWriter, "RNTO " + path.getAbsolutePath());
            if (!readStatus(false) || mNodeRespond.mRespondCode != FtpCodes.NODE_OPSOK) {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, FtpCodes.FTP_FILEFAIL, ' ', "Rename failed.");
            } else {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, mNodeRespond.mStatus.mStatusCode, ' ', mNodeRespond.mStatus.mStatusMsg);
            }
        }
    }

    public boolean doType(boolean isAscii) {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();

            FtpUtil.ftpWriteStringRaw(mWriter, "TYPE " + (isAscii ? "A" : "I"));

            return readAndCheckStatus(false, FtpCodes.NODE_OPSOK, FtpCodes.FTP_TYPEOK);
        }
    }

    public boolean doRest(long offset) {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();

            FtpUtil.ftpWriteStringRaw(mWriter, "REST " + offset);

            return readAndCheckStatus(false, FtpCodes.NODE_OPSOK, FtpCodes.FTP_RESTOK);
        }
    }

    public boolean doCharset(String charset) {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();

            FtpUtil.ftpWriteStringRaw(mWriter, "CSET " + charset);

            return readAndCheckStatus(false, FtpCodes.NODE_OPSOK, FtpCodes.NODE_CHARSETOK);
        }
    }

    public String doUnique(Path path) {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();

            FtpUtil.ftpWriteStringRaw(mWriter, "UQUE " + path.getAbsolutePath());

            if (readAndCheckStatus(false, FtpCodes.NODE_OPSOK, FtpCodes.NODE_UNIQUEOK)) {
                return mNodeRespond.mStatus.mStatusMsg;
            } else {
                return null;
            }
        }
    }

    public void handlePasv() {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();

            FtpUtil.ftpWriteStringRaw(mWriter, "PASV");
            if (!readStatus(false) || mNodeRespond.mRespondCode != FtpCodes.NODE_OPSOK) {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, FtpCodes.FTP_BADCMD, ' ', "Enter Passive Mode Failed.");
            } else {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, mNodeRespond.mStatus.mStatusCode, ' ', mNodeRespond.mStatus.mStatusMsg);
                if (mNodeRespond.mStatus.mStatusCode == FtpCodes.FTP_PASVOK) {
                    mHostServant.mSession.userTransformActivatedNode = mConnector.mHostNodeDefinition.number;
                }
            }
        }
    }

    public void handlePort(String portArg) {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();

            FtpUtil.ftpWriteStringRaw(mWriter, "PORT " + portArg);
            if (!readStatus(false) || mNodeRespond.mRespondCode != FtpCodes.NODE_OPSOK) {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, FtpCodes.FTP_BADCMD, ' ', "Enter Port Mode Failed.");
            } else {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, mNodeRespond.mStatus.mStatusCode, ' ', mNodeRespond.mStatus.mStatusMsg);
                if (mNodeRespond.mStatus.mStatusCode == FtpCodes.FTP_PORTOK) {
                    mHostServant.mSession.userTransformActivatedNode = mConnector.mHostNodeDefinition.number;
                }
            }
        }
    }

    public boolean doPort(String portArg) {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();

            FtpUtil.ftpWriteStringRaw(mWriter, "PORT " + portArg);
            if (!readAndCheckStatus(false, FtpCodes.NODE_OPSOK, FtpCodes.FTP_PORTOK)) {
                System.out.println("Enter port mode failed. PORT " + portArg);
                return false;
            }
            return true;
        }
    }

    public boolean handleBridge(boolean receive, String connectorAddress) {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();

            mBridgePort = -1;
            FtpUtil.ftpWriteStringRaw(mWriter, (receive ? "REVE " : "SEND ") + connectorAddress);
            if (!readStatus(false) || mNodeRespond.mRespondCode != FtpCodes.NODE_OPSOK
                    || mNodeRespond.mStatus.mStatusCode == FtpCodes.NODE_BADBRIDGE) {
                System.out.println("Open bridge failed");
                return false;
            }
            if (mNodeRespond.mStatus.mStatusCode == FtpCodes.NODE_BRIDGEOK) {
                try {
                    mBridgePort = Integer.parseInt(mNodeRespond.mStatus.mStatusMsg);
                } catch (NumberFormatException ignored) {
                    System.out.println("Open bridge failed");
                    return false;
                }
            } else {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, mNodeRespond.mStatus.mStatusCode, ' ', mNodeRespond.mStatus.mStatusMsg);
                return true;
            }
        }
        return true;
    }

    public void handleStat(Path path, String filter) {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();

            FtpUtil.ftpWriteStringCommon(mHostServant.mOut, FtpCodes.FTP_STATFILE_OK, '-', "Status follows:");

            FtpUtil.ftpWriteStringRaw(mWriter, "STAT " + path.getAbsolutePath() + "::" + filter);
            while (readAndCheckStatus(false, FtpCodes.NODE_OPSMSG, FtpCodes.FTP_STATFILE_OK)) {
                FtpUtil.ftpWriteStringRaw(mHostServant.mOut, mNodeRespond.mStatus.mStatusMsg);
            }
            if (mNodeRespond.mRespondCode == FtpCodes.NODE_OPSOK) {
                if (mNodeRespond.mStatus.mStatusCode != FtpCodes.FTP_STATFILE_OK) {
                    FtpUtil.ftpWriteStringCommon(mHostServant.mOut, mNodeRespond.mStatus.mStatusCode, ' ', mNodeRespond.mStatus.mStatusMsg);
                }
            }

            FtpUtil.ftpWriteStringCommon(mHostServant.mOut, FtpCodes.FTP_STATFILE_OK, ' ', "End of status");
        }
    }

    public void handleList(Path path, String filter, boolean fullDetail) {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();

            FtpUtil.ftpWriteStringRaw(mWriter, "LIST " + path.getAbsolutePath() + "::" + filter + "::" + (fullDetail ? 1 : 0));
            if (!readStatus(true) || mNodeRespond.mRespondCode != FtpCodes.NODE_OPSOK) {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to list directory.");
            } else {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, mNodeRespond.mStatus.mStatusCode, ' ', mNodeRespond.mStatus.mStatusMsg);
            }
        }
    }

    public void handleRetr(Path path) {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();

            FtpUtil.ftpWriteStringRaw(mWriter, "RETR " + path.getAbsolutePath());
            if (!readStatus(true) || mNodeRespond.mRespondCode != FtpCodes.NODE_OPSOK) {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to send file.");
            } else {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, mNodeRespond.mStatus.mStatusCode, ' ', mNodeRespond.mStatus.mStatusMsg);
            }
        }
    }

    public void handleStor(Path path, boolean isAppend) {
        synchronized (mWaitObj) {
            mCmdCallTimeStamp = System.currentTimeMillis();

            FtpUtil.ftpWriteStringRaw(mWriter, "STOR " + path.getAbsolutePath() + "::" + (isAppend ? "1" : "0"));
            if (!readStatus(true) || mNodeRespond.mRespondCode != FtpCodes.NODE_OPSOK) {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to get file.");
            } else {
                FtpUtil.ftpWriteStringCommon(mHostServant.mOut, mNodeRespond.mStatus.mStatusCode, ' ', mNodeRespond.mStatus.mStatusMsg);
            }
        }
    }
}
