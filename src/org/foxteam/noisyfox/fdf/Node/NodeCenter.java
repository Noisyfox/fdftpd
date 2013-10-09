package org.foxteam.noisyfox.fdf.Node;

import org.foxteam.noisyfox.fdf.*;

import java.io.*;
import java.net.Socket;
import java.util.LinkedList;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-25
 * Time: 下午8:28
 * To change this template use File | Settings | File Templates.
 */
public class NodeCenter {
    protected final FtpCertification mCert;
    protected final Tunables mTunables;
    protected final Socket mIncoming;
    protected final NodeDirectoryMapper mDirectoryMapper;
    protected PrintWriter mOut;
    protected BufferedReader mIn;

    private RequestCmdArg mHostCmdArg = new RequestCmdArg();

    public NodeCenter(NodeDirectoryMapper directoryMapper, FtpCertification cert, Tunables tunables, Socket socket) {
        mDirectoryMapper = directoryMapper;
        mCert = cert;
        mTunables = tunables;
        mIncoming = socket;
    }


    private boolean readCmdArg() {
        return FtpUtil.readCmdArg(mIncoming, mIn, mHostCmdArg, 0, "Node");
    }

    public void startNode() {
        //开始监听
        try {
            mOut = new PrintWriter(new OutputStreamWriter(mIncoming.getOutputStream(), mTunables.hostRemoteCharset), true);
            mIn = new BufferedReader(new InputStreamReader(mIncoming.getInputStream(), mTunables.hostRemoteCharset));
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            return;
        }

        //验证服务端身份
        if (verifyHost()) {
            serviceLoop();
        }

    }

    public void tryDoClean() {
        try {

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean verifyHost() {
        String challenge = FtpCertification.generateChallenge();
        while (readCmdArg()) {
            if ("CHAG".equals(mHostCmdArg.mCmd)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.HOST_CHALLENGEOK, ' ', challenge);
            } else if ("REPO".equals(mHostCmdArg.mCmd)) {
                if (mCert.verify(challenge, mHostCmdArg.mArg)) {
                    //OK
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.HOST_RESPONDEOK, ' ', "Greeting! Host!");
                    System.out.println("Host verify success!");
                    return true;
                } else {
                    //Bad
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.HOST_BADRESPONDE, ' ', "Failed to verify host.");
                    System.out.println("Failed to verify host.");
                    return false;
                }
            } else {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOPERM, ' ', "Permission denied.");
            }
        }
        return false;
    }

    public void serviceLoop() {
        while (readCmdArg()) {
            if ("DMAP".equals(mHostCmdArg.mCmd)) {
                handleDmap();
            } else if ("NOOP".equals(mHostCmdArg.mCmd)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOOPOK, ' ', "NOOP ok.");
            } else if ("PORT".equals(mHostCmdArg.mCmd)) {
                handlePort();
            } else if ("CONF".equals(mHostCmdArg.mCmd)) {
                mTunables.parseSetting(mHostCmdArg.mArg, true);
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.HOST_CONFOK, ' ', "Config updated.");
            } else {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADCMD, ' ', "Unknown command.");
            }
        }
    }

    private void handleDmap() {
        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.HOST_DMAP, '-', "DirMaps:");
        LinkedList<Pair<Path, Path>> pathPairs = mDirectoryMapper.getAllPathPairs();
        for (Pair<Path, Path> pp : pathPairs) {
            FtpUtil.ftpWriteStringRaw(mOut, " " + pp.getValue2().getAbsolutePath());
        }
        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.HOST_DMAP, ' ', "End");
    }

    private void handlePort() {
        String oAddr = mIncoming.getRemoteSocketAddress().toString();
        String hostAddr = oAddr.substring(1, oAddr.indexOf(':'));
        int hostPort;
        try {
            hostPort = Integer.parseInt(mHostCmdArg.mArg);
        } catch (NumberFormatException e) {
            return;
        }
        try {
            Socket tempSocket = new Socket(hostAddr, hostPort);
            NodeServant servant = new NodeServant(this, tempSocket);
            servant.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
