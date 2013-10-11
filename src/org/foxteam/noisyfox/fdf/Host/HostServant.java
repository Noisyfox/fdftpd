package org.foxteam.noisyfox.fdf.Host;

import org.foxteam.noisyfox.fdf.FilePermission;
import org.foxteam.noisyfox.fdf.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.ParseException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-21
 * Time: 下午10:04
 * To change this template use File | Settings | File Templates.
 */
public class HostServant extends Thread {
    private static volatile AtomicInteger mNumClients = new AtomicInteger(0);

    protected final Host mHost;
    protected final Tunables mTunables;
    protected final Socket mIncoming;
    protected PrintWriter mOut;
    protected BufferedReader mIn;

    protected HostSession mSession;
    protected HostNodeController mNodeController;

    protected HostServant(Host host, Socket socket) {
        mHost = host;
        mTunables = host.getTunables();
        mIncoming = socket;
    }

    //**************************************************************************************************************
    //数据传输流操作

    private boolean ioOpenConnection(String msg) {

        if (!isPortActivate() && !isPasvActivate()) {
            //Oops! Something must go wrong!
            return false;
        }

        mSession.userDataTransferAborReceived = false;

        boolean connectCreated;
        if (isPasvActivate()) {
            connectCreated = ioStartConnectionPasv();
        } else {
            connectCreated = ioStartConnectionPort();
        }

        if (!connectCreated) return false;

        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_DATACONN, ' ', msg);

        return true;
    }

    private void ioCloseConnection() {

        if (mSession.userDataTransferReaderAscii != null) try {
            mSession.userDataTransferReaderAscii.close();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        if (mSession.userDataTransferReaderBinary != null) try {
            mSession.userDataTransferReaderBinary.close();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        if (mSession.userDataTransferWriterAscii != null) mSession.userDataTransferWriterAscii.close();
        if (mSession.userDataTransferWriterBinary != null) try {
            mSession.userDataTransferWriterBinary.close();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        if (mSession.userDataTransferSocket != null) try {
            mSession.userDataTransferSocket.close();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        mSession.userDataTransferSocket = null;
        mSession.userDataTransferReaderAscii = null;
        mSession.userDataTransferReaderBinary = null;
        mSession.userDataTransferWriterAscii = null;
        mSession.userDataTransferWriterBinary = null;
    }

    private boolean ioStartConnectionPasv() {
        Socket tempSocket = null;
        BufferedReader tempReaderAscii = null;
        BufferedInputStream tempReaderBinary = null;
        PrintWriter tempWriterAscii;
        BufferedOutputStream tempWriterBinary;
        try {
            tempSocket = mSession.userPasvSocketServer.accept();
            mSession.userPasvSocketServer.close();
            mSession.userPasvSocketServer = null;
            //检查是否是来自当前客户端的连接
            String clientAddr = FtpUtil.getSocketRemoteAddress(tempSocket);
            if (!mSession.userRemoteAddr.equals(clientAddr)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDCONN, ' ', "Security: Bad IP connecting.");
                try {
                    tempSocket.close();
                } catch (IOException e1) {
                    e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
                return false;
            }
            long maxRate = mSession.userAnon ? mTunables.hostAnonTransferRateMax : mTunables.hostTransferRateMax;
            String charSet = mSession.isUTF8Required ? "UTF-8" : mTunables.hostDefaultTransferCharset; //使用默认编码
            InputStream is = new RateRestrictedInputStream(tempSocket.getInputStream(), mSession, maxRate);
            tempReaderAscii = new BufferedReader(new InputStreamReader(is, charSet));
            tempReaderBinary = new BufferedInputStream(is);
            OutputStream os = new RateRestrictedOutputStream(tempSocket.getOutputStream(), mSession, maxRate);
            tempWriterAscii = new PrintWriter(new BufferedWriter(new OutputStreamWriter(os, charSet)), true);
            tempWriterBinary = new BufferedOutputStream(os);
        } catch (IOException e) {
            e.printStackTrace();
            //clean
            if (tempReaderAscii != null) try {
                tempReaderAscii.close();
            } catch (IOException ignored) {
            }
            if (tempReaderBinary != null) try {
                tempReaderBinary.close();
            } catch (IOException ignored) {
            }
            if (tempSocket != null) try {
                tempSocket.close();
            } catch (IOException ignored) {
            }
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDCONN, ' ', "Failed to establish connection.");

            return false;
        }

        mSession.userDataTransferSocket = tempSocket;
        mSession.userDataTransferReaderAscii = tempReaderAscii;
        mSession.userDataTransferReaderBinary = tempReaderBinary;
        mSession.userDataTransferWriterAscii = tempWriterAscii;
        mSession.userDataTransferWriterBinary = tempWriterBinary;

        return true;
    }

    private boolean ioStartConnectionPort() {
        Socket tempSocket = null;
        BufferedReader tempReaderAscii = null;
        BufferedInputStream tempReaderBinary = null;
        PrintWriter tempWriterAscii;
        BufferedOutputStream tempWriterBinary;
        try {
            long maxRate = mSession.userAnon ? mTunables.hostAnonTransferRateMax : mTunables.hostTransferRateMax;
            tempSocket = new Socket(mSession.userPortSocketAddr, mSession.userPortSocketPort);
            String charSet = mSession.isUTF8Required ? "UTF-8" : mTunables.hostDefaultTransferCharset; //使用默认编码
            InputStream is = new RateRestrictedInputStream(tempSocket.getInputStream(), mSession, maxRate);
            tempReaderAscii = new BufferedReader(new InputStreamReader(is, charSet));
            tempReaderBinary = new BufferedInputStream(is);
            OutputStream os = new RateRestrictedOutputStream(tempSocket.getOutputStream(), mSession, maxRate);
            tempWriterAscii = new PrintWriter(new BufferedWriter(new OutputStreamWriter(os, charSet)), true);
            tempWriterBinary = new BufferedOutputStream(os);
        } catch (IOException e) {
            e.printStackTrace();
            //clean
            if (tempReaderAscii != null) try {
                tempReaderAscii.close();
            } catch (IOException ignored) {
            }
            if (tempReaderBinary != null) try {
                tempReaderBinary.close();
            } catch (IOException ignored) {
            }
            if (tempSocket != null) try {
                tempSocket.close();
            } catch (IOException ignored) {
            }
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDCONN, ' ', "Failed to establish connection.");

            return false;
        }
        mSession.userDataTransferSocket = tempSocket;
        mSession.userDataTransferReaderAscii = tempReaderAscii;
        mSession.userDataTransferReaderBinary = tempReaderBinary;
        mSession.userDataTransferWriterAscii = tempWriterAscii;
        mSession.userDataTransferWriterBinary = tempWriterBinary;
        return true;
    }

    //**************************************************************************************************************

    private void doClean() {
        if (mOut != null) mOut.close();
        mOut = null;

        if (mIn != null) try {
            mIn.close();
        } catch (IOException ignored) {
        }
        mIn = null;

        try {
            mIncoming.close();
        } catch (IOException ignored) {
        }

        cleanPasv();
        cleanPort();
        ioCloseConnection();

        mNodeController.killAll();

    }

    private boolean readCmdArg() {
        return FtpUtil.readCmdArg(mIncoming, mIn, mSession.mFtpCmdArg, 0, "User:" + mSession.user);
    }

    private boolean checkLimits() {
        if (mTunables.hostMaxClients > 0 && mNumClients.get() > mTunables.hostMaxClients) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_TOO_MANY_USERS, ' ', "There are too many connected users, please try later.");
            return false;
        }
        return true;
    }

    private void greeting() {
        if (mTunables.hostFtpdBanner.isEmpty()) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_GREET, ' ', "(fdFTPd " + FtpMain.FDF_VER + " )");
        } else {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_GREET, ' ', mTunables.hostFtpdBanner);
        }
    }

    private boolean checkLoginFail() {
        mSession.loginFails++;
        return mSession.loginFails <= mTunables.hostMaxLoginFails;
    }

    private boolean checkFileAccess(Path file, int operation) {
        //验证是否为home的子目录
        if (!file.isChildPath(mSession.userHomeDir)) {
            return false;
        }

        //匿名用户权限已经在主代码中控制过了，此处就直接略过
        return mSession.userAnon || mSession.permission.checkAccess(file, operation);
    }

    private void handleCwd() {
        //获取真实路径
        Path rp = FtpUtil.ftpGetRealPath(mSession.userHomeDir, mSession.userCurrentDir, Path.valueOf(mSession.mFtpCmdArg.mArg));
        //先检测权限
        if (!checkFileAccess(rp, FilePermission.OPERATION_DIRECTORY_CHANGE)) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOPERM, ' ', "Permission denied.");
            return;
        }
        //然后判断目标目录位置
        int pathNode = mHost.getDirMapper().map(rp);
        if (pathNode == -1) {
            File f = rp.getFile();

            if (!f.exists() || !f.isDirectory()) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to change directory.");
                return;
            }

            mSession.userCurrentDir = rp;
            mSession.userCurrentDirNode = -1;

            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_CWDOK, ' ', "Directory successfully changed.");
        } else {
            try {
                mNodeController.chooseNode(pathNode);
                HostNodeSession nodeSession = mNodeController.getNodeSession();
                nodeSession.handleCwd(rp);
            } catch (IndexOutOfBoundsException ex) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to change directory.");
            }
        }
    }

    private void cleanPasv() {
        mSession.userTransformActivatedNode = -1;
        if (mSession.userPasvSocketServer != null) {
            try {
                mSession.userPasvSocketServer.close();
            } catch (IOException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
        mSession.userPasvSocketServer = null;
    }

    private void cleanPort() {
        mSession.userTransformActivatedNode = -1;
        mSession.userPortSocketAddr = "";
        mSession.userPortSocketPort = 0;
    }

    private boolean isPasvActivate() {
        return mSession.userPasvSocketServer != null && !mSession.userPasvSocketServer.isClosed();
    }

    private boolean isPortActivate() {
        return !mSession.userPortSocketAddr.isEmpty();
    }

    private boolean checkDataTransferOk() {
        if (!isPasvActivate() && !isPortActivate()
                && mSession.userTransformActivatedNode == -1) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDCONN, ' ', "Use PORT or PASV first.");
            return false;
        }
        return true;
    }

    private void checkAbort() {
        if (mSession.userDataTransferAborReceived) {
            mSession.userDataTransferAborReceived = false;
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_ABOROK, ' ', "ABOR successful.");
        }
    }

    private void handlePasv() {
        cleanPasv();
        cleanPort();

        //判断当前用户所在目录位置
        int pathNode = mSession.userCurrentDirNode;

        if (pathNode == -1) {
            //尝试开启端口监听
            ServerSocket ss = FtpUtil.openRandomPort();
            if (ss == null) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADCMD, ' ',
                        "Enter Passive Mode Failed.");
                return;
            }
            int selectedPort = ss.getLocalPort();

            String address = mIncoming.getLocalAddress().toString().replace("/", "").replace(".", ",");
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_PASVOK, ' ', "Entering Passive Mode (" + address + ","
                    + (selectedPort >> 8) + "," + (selectedPort & 0xFF) + ").");
            //储存pasv监听器
            mSession.userPasvSocketServer = ss;
        } else {
            try {
                mNodeController.chooseNode(pathNode);
                HostNodeSession nodeSession = mNodeController.getNodeSession();
                nodeSession.handlePasv();
            } catch (IndexOutOfBoundsException ex) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to change directory.");
            }
        }
    }

    private void handlePort() {
        cleanPasv();
        cleanPort();

        //判断当前用户所在目录位置
        int pathNode = mSession.userCurrentDirNode;

        if (pathNode == -1) {
            String[] values = mSession.mFtpCmdArg.mArg.split(",");
            if (values.length != 6) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADCMD, ' ', "Illegal PORT command.");
                return;
            }
            int sockPort;
            try {
                sockPort = Integer.valueOf(values[4]) << 8 | Integer.valueOf(values[5]);
            } catch (NumberFormatException e) {
                e.printStackTrace();
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADCMD, ' ', "Illegal PORT command.");
                return;
            }
            String sockAddr = String.format("%s.%s.%s.%s", values);
            System.out.println(sockAddr + ":" + sockPort);
            /* SECURITY:
            * 1) Reject requests not connecting to the control socket IP
            * 2) Reject connects to privileged ports
            */
            if (!sockAddr.equals(mSession.userRemoteAddr) || sockPort < FtpMain.IPPORT_RESERVED) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADCMD, ' ', "Illegal PORT command.");
                return;
            }

            mSession.userPortSocketAddr = sockAddr;
            mSession.userPortSocketPort = sockPort;

            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_PORTOK, ' ', "PORT command successful. Consider using PASV.");
        } else {
            try {
                mNodeController.chooseNode(pathNode);
                HostNodeSession nodeSession = mNodeController.getNodeSession();
                nodeSession.handlePort(mSession.mFtpCmdArg.mArg);
            } catch (IndexOutOfBoundsException ex) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to change directory.");
            }
        }
    }

    //***********************************************************************************************************************************
    private void handleStat(Path path, String filter) {
        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_STATFILE_OK, '-', "Status follows:");

        FtpUtil.outPutDir(mOut, path, filter,
                true, mSession.userAnon, mTunables, mSession.permission, "");

        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_STATFILE_OK, ' ', "End of status");
    }

    private void handleDir(Path path, String filter, boolean fullDetails) {

        //开始传输数据
        if (!ioOpenConnection("Here comes the directory listing.")) {
            cleanPasv();
            cleanPort();
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to list directory.");
            return;
        }

        //开始列举目录
        boolean transferSuccess = FtpUtil.outPutDir(mSession.userDataTransferWriterAscii, path, filter,
                fullDetails, mSession.userAnon, mTunables, mSession.permission, "");

        ioCloseConnection();

        if (!transferSuccess) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failure writing network stream.");
        } else {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_TRANSFEROK, ' ', "Directory send OK.");
        }

        checkAbort();

        cleanPasv();
        cleanPort();
    }

    /**
     * 客户端连接的是node，而目标文件在host上
     *
     * @param node        客户端连接的node
     * @param path        目标文件的path
     * @param filter      过滤器
     * @param fullDetails 是否是完整信息
     */
    private void handleDirHostNode(int node, Path path, String filter, boolean fullDetails) {
        try {
            mNodeController.chooseNode(node);
            HostNodeSession nodeSession = mNodeController.getNodeSession();
            if (!nodeSession.handleBridge(true, mHost.getHostAddress())) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to list directory.");
                return;
            }
            if (nodeSession.mBridgePort == -1) {
                return;
            }
            // bridge已建立
            // 伪造port
            cleanPort();
            cleanPasv();
            mSession.userPortSocketAddr = nodeSession.getNodeAddress();
            mSession.userPortSocketPort = nodeSession.mBridgePort;

            handleDir(path, filter, fullDetails);
        } catch (IndexOutOfBoundsException ex) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to list directory.");
        }
    }

    /**
     * 客户端连接的是host，而目标文件在node上
     *
     * @param node        目标文件所在的node
     * @param path        目标文件的path
     * @param filter      过滤器
     * @param fullDetails 是否是完整信息
     */
    private void handleDirNodeHost(int node, Path path, String filter, boolean fullDetails) {
        HostNodeSession nodeSession;
        try {
            mNodeController.chooseNode(node);
            nodeSession = mNodeController.getNodeSession();
        } catch (IndexOutOfBoundsException ex) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to list directory.");
            return;
        }

        //建立本地桥
        if (mSession.userCurrentActivatedBridge != null && !mSession.userCurrentActivatedBridge.isDead()) {
            mSession.userCurrentActivatedBridge.kill();
        }
        ServerSocket listener = FtpUtil.openRandomPort();
        if (listener == null) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to list directory.");
            return;
        }

        mSession.userCurrentActivatedBridge = new FtpBridge(listener, nodeSession.getNodeAddress(), new FtpBridge.OnBridgeWorkFinishListener() {
            @Override
            public void onWorkFinish() {
                ioCloseConnection();
                cleanPasv();
                cleanPort();
                mSession.userCurrentActivatedBridge = null;
            }
        });

        //开始传输数据
        if (!ioOpenConnection("Here comes the directory listing.")) {
            cleanPasv();
            cleanPort();
            mSession.userCurrentActivatedBridge.kill();
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failed to establish connection.");
            return;
        }
        String myAddr = mHost.getHostAddress();
        String portArg = myAddr.replace(".", ",");
        int portNum = listener.getLocalPort();
        portArg += "," + (portNum >> 8) + "," + (portNum & 0xFF);
        if (!nodeSession.doPort(portArg)) {
            cleanPasv();
            cleanPort();
            mSession.userCurrentActivatedBridge.kill();
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failed to establish connection.");
            return;
        }

        mSession.userCurrentActivatedBridge.startForReceiving(mSession.userDataTransferWriterBinary);
        //通知编码
        nodeSession.doCharset(mSession.isUTF8Required ? "UTF-8" : mTunables.hostDefaultTransferCharset);
        nodeSession.handleList(path, filter, fullDetails);
    }

    /**
     * 客户端连接的是activeNode，而目标文件在pathNode上
     *
     * @param pathNode    目标文件所在的node
     * @param activeNode  客户端连接的node
     * @param path        目标文件的path
     * @param filter      过滤器
     * @param fullDetails 是否是完整信息
     */
    private void handleDirNodeNode(int pathNode, int activeNode, Path path, String filter, boolean fullDetails) {
        if (pathNode == activeNode) {
            try {
                mNodeController.chooseNode(pathNode);
                HostNodeSession nodeSession = mNodeController.getNodeSession();
                //通知编码
                nodeSession.doCharset(mSession.isUTF8Required ? "UTF-8" : mTunables.hostDefaultTransferCharset);
                nodeSession.handleList(path, filter, fullDetails);
            } catch (IndexOutOfBoundsException ex) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to list directory.");
            }
        } else {
            HostNodeSession pathNodeSession, activeNodeSession;
            try {
                mNodeController.chooseNode(pathNode);
                pathNodeSession = mNodeController.getNodeSession();
                mNodeController.chooseNode(activeNode);
                activeNodeSession = mNodeController.getNodeSession();
            } catch (IndexOutOfBoundsException ex) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to list directory.");
                return;
            }

            if (!activeNodeSession.handleBridge(true, pathNodeSession.getNodeAddress())) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to list directory.");
                return;
            }
            if (activeNodeSession.mBridgePort == -1) {
                return;
            }

            String myAddr = pathNodeSession.getNodeAddress();
            String portArg = myAddr.replace(".", ",");
            int portNum = activeNodeSession.mBridgePort;
            portArg += "," + (portNum >> 8) + "," + (portNum & 0xFF);
            if (!pathNodeSession.doPort(portArg)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to list directory.");
                return;
            }

            //通知编码
            pathNodeSession.doCharset(mSession.isUTF8Required ? "UTF-8" : mTunables.hostDefaultTransferCharset);
            pathNodeSession.handleList(path, filter, fullDetails);
        }
    }

    private void handleDirCommon(boolean fullDetails, boolean statCmd) {
        if (!statCmd && !checkDataTransferOk()) {
            return;
        }

        String optionStr = "";
        String filterStr;
        if (!mSession.mFtpCmdArg.mArg.isEmpty() && mSession.mFtpCmdArg.mArg.startsWith("-")) {
            int spaceIndex = mSession.mFtpCmdArg.mArg.indexOf(' ');
            if (spaceIndex != -1) {
                optionStr = mSession.mFtpCmdArg.mArg.substring(1, spaceIndex);
                filterStr = mSession.mFtpCmdArg.mArg.substring(spaceIndex).trim();
            } else {
                optionStr = mSession.mFtpCmdArg.mArg;
                filterStr = "";
            }
        } else {
            filterStr = mSession.mFtpCmdArg.mArg;
        }

        //解析filter
        //找到第一个最后一级不包含通配符的路径
        filterStr = filterStr.replace('\\', '/');//全部转成/分割
        int firstWildcard1 = filterStr.indexOf('?');
        int firstWildcard2 = filterStr.indexOf('*');
        int firstWildcard;
        if (firstWildcard1 == -1 && firstWildcard2 == -1) {
            firstWildcard = -1;
        } else if (firstWildcard1 == -1) {
            firstWildcard = firstWildcard2;
        } else if (firstWildcard2 == -1) {
            firstWildcard = firstWildcard1;
        } else {
            firstWildcard = Math.min(firstWildcard1, firstWildcard2);
        }
        String newPath;
        if (firstWildcard == -1) {
            newPath = filterStr;
            filterStr = "";
        } else {
            int lastSplashBeforeWildcard = filterStr.lastIndexOf('/', firstWildcard);
            if (lastSplashBeforeWildcard == -1) {
                newPath = ".";
            } else {
                newPath = filterStr.substring(0, lastSplashBeforeWildcard);
                filterStr = filterStr.substring(lastSplashBeforeWildcard + 1);
                if (newPath.isEmpty()) {
                    newPath = ".";
                }
            }
        }

        Path dirNamePath = FtpUtil.ftpGetRealPath(mSession.userHomeDir, mSession.userCurrentDir, Path.valueOf(newPath));

        if (!checkFileAccess(dirNamePath, FilePermission.OPERATION_DIRECTORY_LIST)) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOPERM, ' ', "Permission denied.");
            return;
        }

        //然后判断目标目录位置
        int pathNode = mHost.getDirMapper().map(dirNamePath);

        if (statCmd) {
            if (pathNode == -1) {
                handleStat(dirNamePath, filterStr);
            } else {
                try {
                    mNodeController.chooseNode(pathNode);
                    HostNodeSession nodeSession = mNodeController.getNodeSession();
                    nodeSession.handleStat(dirNamePath, filterStr);
                } catch (IndexOutOfBoundsException ex) {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADCMD, ' ', "Failed to get stat.");
                }
            }
            return;
        }


        if (pathNode == -1) {
            if (mSession.userTransformActivatedNode == -1) {
                handleDir(dirNamePath, filterStr, fullDetails);
            } else {
                handleDirHostNode(mSession.userTransformActivatedNode, dirNamePath, filterStr, fullDetails);
            }
        } else {
            if (mSession.userTransformActivatedNode == -1) {
                handleDirNodeHost(pathNode, dirNamePath, filterStr, fullDetails);
            } else {
                handleDirNodeNode(pathNode, mSession.userTransformActivatedNode, dirNamePath, filterStr, fullDetails);
            }
        }
    }
    //***********************************************************************************************************************************

    private void handleSize() {
        //获取真实路径
        Path rp = FtpUtil.ftpGetRealPath(mSession.userHomeDir, mSession.userCurrentDir, Path.valueOf(mSession.mFtpCmdArg.mArg));
        if (!checkFileAccess(rp, FilePermission.OPERATION_FILE_DIR_READ)) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOPERM, ' ', "Permission denied.");
            return;
        }
        int pathNode = mHost.getDirMapper().map(rp);
        if (pathNode == -1) {
            File f = rp.getFile();
            if (!f.exists() || f.isDirectory()) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Could not get file size.");
                return;
            }

            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_SIZEOK, ' ', String.valueOf(f.length()));
        } else {
            try {
                mNodeController.chooseNode(pathNode);
                HostNodeSession nodeSession = mNodeController.getNodeSession();
                nodeSession.handleSize(rp);
            } catch (IndexOutOfBoundsException ex) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to change directory.");
            }
        }
    }

    private void handleMdtm() {
        Path fileName = null;
        Date modifyTime = null;
        boolean isSet = false;
        //解析命令，判断是获取还是设置时间
        int firstSpaceLoc = mSession.mFtpCmdArg.mArg.indexOf(' ');
        if (firstSpaceLoc == 14) {
            String timeStr = mSession.mFtpCmdArg.mArg.substring(0, firstSpaceLoc);
            try {
                modifyTime = FtpUtil.dateFormatMdtm.parse(timeStr);
                fileName = Path.valueOf(mSession.mFtpCmdArg.mArg.substring(firstSpaceLoc).trim());
                isSet = true;
            } catch (ParseException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
        if (!isSet) {
            fileName = Path.valueOf(mSession.mFtpCmdArg.mArg);
        }
        //获取真实路径
        Path rp = FtpUtil.ftpGetRealPath(mSession.userHomeDir, mSession.userCurrentDir, fileName);
        int operate = isSet ? FilePermission.OPERATION_FILE_DIR_WRITE : FilePermission.OPERATION_FILE_DIR_READ;
        if (!checkFileAccess(rp, operate)) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOPERM, ' ', "Permission denied.");
            return;
        }

        int pathNode = mHost.getDirMapper().map(rp);
        if (pathNode == -1) {
            File f = rp.getFile();
            if (!f.exists() || f.isDirectory()) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ',
                        isSet ? "Could not set file modification time." : "Could not get file modification time.");
                return;
            }

            if (isSet) {
                try {
                    f.setLastModified(modifyTime.getTime());
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_MDTMOK, ' ', "File modification time set.");
                } catch (Exception e) {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Could not set file modification time.");
                }
            } else {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_MDTMOK, ' ', FtpUtil.dateFormatMdtm.format(new Date(f.lastModified())));
            }
        } else {
            try {
                mNodeController.chooseNode(pathNode);
                HostNodeSession nodeSession = mNodeController.getNodeSession();
                nodeSession.handleMdtm(rp, modifyTime == null ? -1 : modifyTime.getTime());
            } catch (IndexOutOfBoundsException ex) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ',
                        isSet ? "Could not set file modification time." : "Could not get file modification time.");
            }
        }
    }

    //***********************************************************************************************************************************
    private void handleDownloadAsciiCommon(Path path) {
        File f = path.getFile();
        BufferedReader br;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(f),
                    mSession.isUTF8Required ? "UTF-8" : mTunables.hostDefaultTransferCharset));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to open file.");
            return;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to open file.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Opening ASCII mode data connection for ");
        sb.append(mSession.mFtpCmdArg.mArg);
        sb.append(" (");
        sb.append(f.length());
        sb.append(" bytes).");
        if (!ioOpenConnection(sb.toString())) {
            cleanPasv();
            cleanPort();
            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to establish connection.");
            return;
        }

        boolean transferSuccess = true;
        boolean fileReadSuccess = true;
        String line;
        try {
            while ((line = br.readLine()) != null) {
                mSession.userDataTransferWriterAscii.println(line);
                if (!(transferSuccess = !mSession.userDataTransferWriterAscii.checkError())) {
                    break;
                }
            }
            if (transferSuccess) {
                mSession.userDataTransferWriterAscii.flush();
                transferSuccess = !mSession.userDataTransferWriterAscii.checkError();
            }
        } catch (IOException e) {
            e.printStackTrace();
            fileReadSuccess = false;
        }

        if (!fileReadSuccess) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDFILE, ' ', "Failure reading local file.");
        } else if (!transferSuccess) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failure writing network stream.");
        } else {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_TRANSFEROK, ' ', "Transfer complete.");
        }

        ioCloseConnection();

        checkAbort();

        cleanPasv();
        cleanPort();

        try {
            br.close();
        } catch (IOException ignored) {
        }
    }

    private void handleDownloadBinaryCommon(Path path, long offset) {
        File f = path.getFile();
        BufferedInputStream bis;
        try {
            bis = new BufferedInputStream(new FileInputStream(f));
            try {
                bis.skip(offset);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to open file.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Opening BINARY mode data connection for ");
        sb.append(mSession.mFtpCmdArg.mArg);
        sb.append(" (");
        sb.append(f.length());
        sb.append(" bytes).");
        if (!ioOpenConnection(sb.toString())) {
            cleanPasv();
            cleanPort();
            try {
                bis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to establish connection.");
            return;
        }

        boolean transferSuccess = true;
        boolean fileReadSuccess = true;
        byte[] bytes = new byte[10240];
        int size;
        try {
            while ((size = bis.read(bytes)) != -1) {
                try {
                    mSession.userDataTransferWriterBinary.write(bytes, 0, size);
                } catch (IOException e) {
                    e.printStackTrace();
                    transferSuccess = false;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            fileReadSuccess = false;
        }
        try {
            mSession.userDataTransferWriterBinary.flush();
        } catch (IOException e) {
            e.printStackTrace();
            transferSuccess = false;
        }

        if (!fileReadSuccess) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDFILE, ' ', "Failure reading local file.");
        } else if (!transferSuccess) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failure writing network stream.");
        } else {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_TRANSFEROK, ' ', "Transfer complete.");
        }

        ioCloseConnection();

        checkAbort();

        cleanPasv();
        cleanPort();

        try {
            bis.close();
        } catch (IOException ignored) {
        }
    }

    private void handleDownloadHostNode(int node, Path path, boolean ascii, long offset) {
        try {
            mNodeController.chooseNode(node);
            HostNodeSession nodeSession = mNodeController.getNodeSession();
            if (!nodeSession.handleBridge(true, mHost.getHostAddress())) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failed to establish connection.");
                return;
            }
            if (nodeSession.mBridgePort == -1) {
                return;
            }
            // bridge已建立
            // 伪造port
            cleanPort();
            cleanPasv();
            mSession.userPortSocketAddr = nodeSession.getNodeAddress();
            mSession.userPortSocketPort = nodeSession.mBridgePort;

            if (ascii) {
                handleDownloadAsciiCommon(path);
            } else {
                handleDownloadBinaryCommon(path, offset);
            }
        } catch (IndexOutOfBoundsException ex) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failed to establish connection.");
        }
    }

    private void handleDownloadNodeHost(int node, Path path, boolean ascii, long offset) {
        HostNodeSession nodeSession;
        try {
            mNodeController.chooseNode(node);
            nodeSession = mNodeController.getNodeSession();
        } catch (IndexOutOfBoundsException ex) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failed to establish connection.");
            return;
        }

        //建立本地桥
        if (mSession.userCurrentActivatedBridge != null && !mSession.userCurrentActivatedBridge.isDead()) {
            mSession.userCurrentActivatedBridge.kill();
        }
        ServerSocket listener = FtpUtil.openRandomPort();
        if (listener == null) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failed to establish connection.");
            return;
        }

        mSession.userCurrentActivatedBridge = new FtpBridge(listener, nodeSession.getNodeAddress(), new FtpBridge.OnBridgeWorkFinishListener() {
            @Override
            public void onWorkFinish() {
                ioCloseConnection();
                cleanPasv();
                cleanPort();
                mSession.userCurrentActivatedBridge = null;
            }
        });

        //ascii模式则发送编码信息
        if (ascii) {
            //通知编码
            nodeSession.doCharset(mSession.isUTF8Required ? "UTF-8" : mTunables.hostDefaultTransferCharset);
        }
        //发送 offset 和 type 信息
        if (!nodeSession.doRest(offset)) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failed to establish connection.");
            mSession.userCurrentActivatedBridge.kill();
            return;
        }
        if (!nodeSession.doType(ascii)) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failed to establish connection.");
            mSession.userCurrentActivatedBridge.kill();
            return;
        }
        //获取文件长度
        long size = nodeSession.doSize(path);
        if (size < 0) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to read file.");
            mSession.userCurrentActivatedBridge.kill();
            return;
        }

        //开始传输数据
        StringBuilder sb = new StringBuilder();
        sb.append("Opening ");
        sb.append(ascii ? "ASCII" : "BINARY");
        sb.append(" mode data connection for ");
        sb.append(mSession.mFtpCmdArg.mArg);
        sb.append(" (");
        sb.append(size);
        sb.append(" bytes).");
        if (!ioOpenConnection(sb.toString())) {
            cleanPasv();
            cleanPort();
            mSession.userCurrentActivatedBridge.kill();
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failed to establish connection.");
            return;
        }
        String myAddr = mHost.getHostAddress();
        String portArg = myAddr.replace(".", ",");
        int portNum = listener.getLocalPort();
        portArg += "," + (portNum >> 8) + "," + (portNum & 0xFF);
        if (!nodeSession.doPort(portArg)) {
            cleanPasv();
            cleanPort();
            mSession.userCurrentActivatedBridge.kill();
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failed to establish connection.");
            return;
        }

        mSession.userCurrentActivatedBridge.startForReceiving(mSession.userDataTransferWriterBinary);
        nodeSession.handleRetr(path);
    }

    private void handleDownloadNodeNode(int pathNode, int activeNode, Path path, boolean ascii, long offset) {
        if (pathNode == activeNode) {
            try {
                mNodeController.chooseNode(pathNode);
                HostNodeSession nodeSession = mNodeController.getNodeSession();
                //ascii模式则发送编码信息
                if (ascii) {
                    //通知编码
                    nodeSession.doCharset(mSession.isUTF8Required ? "UTF-8" : mTunables.hostDefaultTransferCharset);
                }
                //发送 offset 和 type 信息
                if (!nodeSession.doRest(offset)) {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failed to establish connection.");
                    mSession.userCurrentActivatedBridge.kill();
                    return;
                }
                if (!nodeSession.doType(ascii)) {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failed to establish connection.");
                    mSession.userCurrentActivatedBridge.kill();
                    return;
                }
                //获取文件长度
                long size = nodeSession.doSize(path);
                if (size < 0) {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to read file.");
                    mSession.userCurrentActivatedBridge.kill();
                    return;
                }

                //开始传输数据
                StringBuilder sb = new StringBuilder();
                sb.append("Opening ");
                sb.append(ascii ? "ASCII" : "BINARY");
                sb.append(" mode data connection for ");
                sb.append(mSession.mFtpCmdArg.mArg);
                sb.append(" (");
                sb.append(size);
                sb.append(" bytes).");
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_DATACONN, ' ', sb.toString());

                nodeSession.handleRetr(path);
            } catch (IndexOutOfBoundsException ex) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failed to establish connection.");
            }
        } else {
            HostNodeSession pathNodeSession, activeNodeSession;
            try {
                mNodeController.chooseNode(pathNode);
                pathNodeSession = mNodeController.getNodeSession();
                mNodeController.chooseNode(activeNode);
                activeNodeSession = mNodeController.getNodeSession();
            } catch (IndexOutOfBoundsException ex) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failed to establish connection.");
                return;
            }
            if (!activeNodeSession.handleBridge(true, pathNodeSession.getNodeAddress())) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failed to establish connection.");
                return;
            }

            if (activeNodeSession.mBridgePort == -1) {
                return;
            }

            String myAddr = pathNodeSession.getNodeAddress();
            String portArg = myAddr.replace(".", ",");
            int portNum = activeNodeSession.mBridgePort;
            portArg += "," + (portNum >> 8) + "," + (portNum & 0xFF);
            if (!pathNodeSession.doPort(portArg)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failed to establish connection.");
                return;
            }

            //ascii模式则发送编码信息
            if (ascii) {
                //通知编码
                pathNodeSession.doCharset(mSession.isUTF8Required ? "UTF-8" : mTunables.hostDefaultTransferCharset);
            }
            //发送 offset 和 type 信息
            if (!pathNodeSession.doRest(offset)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failed to establish connection.");
                mSession.userCurrentActivatedBridge.kill();
                return;
            }
            if (!pathNodeSession.doType(ascii)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failed to establish connection.");
                mSession.userCurrentActivatedBridge.kill();
                return;
            }
            //获取文件长度
            long size = pathNodeSession.doSize(path);
            if (size < 0) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to read file.");
                mSession.userCurrentActivatedBridge.kill();
                return;
            }

            //开始传输数据
            StringBuilder sb = new StringBuilder();
            sb.append("Opening ");
            sb.append(ascii ? "ASCII" : "BINARY");
            sb.append(" mode data connection for ");
            sb.append(mSession.mFtpCmdArg.mArg);
            sb.append(" (");
            sb.append(size);
            sb.append(" bytes).");
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_DATACONN, ' ', sb.toString());

            pathNodeSession.handleRetr(path);
        }
    }

    private void handleRetr() {
        if (!checkDataTransferOk()) {
            return;
        }

        boolean useAscii = mSession.isAscii && mTunables.hostAsciiDownloadEnabled;

        long fileOffset = mSession.userFileRestartOffset;
        mSession.userFileRestartOffset = 0;
        if (useAscii && fileOffset != 0) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "No support for resume of ASCII transfer.");
            return;
        }

        //获取真实路径
        Path rp = FtpUtil.ftpGetRealPath(mSession.userHomeDir, mSession.userCurrentDir, Path.valueOf(mSession.mFtpCmdArg.mArg));
        if (!checkFileAccess(rp, FilePermission.OPERATION_FILE_READ)) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOPERM, ' ', "Permission denied.");
            return;
        }

        int pathNode = mHost.getDirMapper().map(rp);
        if (pathNode == -1) {
            if (mSession.userTransformActivatedNode == -1) {
                if (useAscii) {
                    handleDownloadAsciiCommon(rp);
                } else {
                    handleDownloadBinaryCommon(rp, fileOffset);
                }
            } else {
                handleDownloadHostNode(mSession.userTransformActivatedNode, rp, useAscii, fileOffset);
            }
        } else {
            if (mSession.userTransformActivatedNode == -1) {
                handleDownloadNodeHost(pathNode, rp, useAscii, fileOffset);
            } else {
                handleDownloadNodeNode(pathNode, mSession.userTransformActivatedNode, rp, useAscii, fileOffset);
            }
        }
    }

    //***********************************************************************************************************************************
    private void handleUploadCommon(boolean isAppend, boolean isUnique) {
        if (!checkDataTransferOk()) {
            return;
        }

        long fileOffset = mSession.userFileRestartOffset;
        mSession.userFileRestartOffset = 0;

        //获取真实路径
        if (mSession.mFtpCmdArg.mArg.isEmpty()) {
            mSession.mFtpCmdArg.mArg = "STOU";
        }
        Path rp = FtpUtil.ftpGetRealPath(mSession.userHomeDir, mSession.userCurrentDir, Path.valueOf(mSession.mFtpCmdArg.mArg));
        int operate = (fileOffset != 0 || isAppend) ? FilePermission.OPERATION_FILE_WRITE : FilePermission.OPERATION_FILE_CREATE;
        if (!checkFileAccess(rp, operate)) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOPERM, ' ', "Permission denied.");
            return;
        }

        File f = rp.getFile();
        {
            File nf = f;
            int suffix = 1;
            while (nf.exists() && isUnique) {//增加后缀避免重名
                nf = new File(rp.getAbsolutePath() + "." + suffix);
                suffix++;
            }
            f = nf;
        }
        RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(f, "rw");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_UPLOADFAIL, ' ', "Could not create file or open exist file.");
            return;
        }

        try {
            if (!isAppend && fileOffset != 0) {
                raf.seek(fileOffset);
            } else if (isAppend) {
                raf.seek(raf.length());
            } else {
                raf.setLength(0);//清除已有内容
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        String ioMsg;
        if (isUnique) {
            ioMsg = "FILE: " + f.getName();
        } else {
            ioMsg = "Ok to send data.";
        }
        if (!ioOpenConnection(ioMsg)) {
            cleanPasv();
            cleanPort();
            try {
                raf.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //接收数据
        boolean transferSuccess = false;
        boolean fileWriteSuccess = true;
        try {
            if (mSession.isAscii && mTunables.hostAsciiUploadEnabled) {
                String charSet = mSession.isUTF8Required ? "UTF-8" : mTunables.hostDefaultTransferCharset; //使用默认编码
                String line;
                while ((line = mSession.userDataTransferReaderAscii.readLine()) != null) {
                    byte[] bytes = line.getBytes(charSet);
                    transferSuccess = true;
                    fileWriteSuccess = false;
                    raf.write(bytes);
                    transferSuccess = false;
                    fileWriteSuccess = true;
                }
                transferSuccess = true;
                fileWriteSuccess = true;
            } else {
                byte[] bytes = new byte[10240];
                int size;
                while ((size = mSession.userDataTransferReaderBinary.read(bytes)) != -1) {
                    transferSuccess = true;
                    fileWriteSuccess = false;
                    raf.write(bytes, 0, size);
                    transferSuccess = false;
                    fileWriteSuccess = true;
                }
                transferSuccess = true;
                fileWriteSuccess = true;
            }
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        if (!fileWriteSuccess) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDFILE, ' ', "Failure writing to local file.");
        } else if (!transferSuccess) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failure reading network stream.");
        } else {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_TRANSFEROK, ' ', "Transfer complete.");
        }

        ioCloseConnection();
        checkAbort();
        cleanPasv();
        cleanPort();
        try {
            raf.close();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
    //***********************************************************************************************************************************

    private void handleFeatures() {
        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FEAT, '-', "Features:");
        FtpUtil.ftpWriteStringRaw(mOut, " MDTM");
        if (mTunables.hostPasvEnabled) {
            FtpUtil.ftpWriteStringRaw(mOut, " PASV");
        }
        FtpUtil.ftpWriteStringRaw(mOut, " REST STREAM");
        FtpUtil.ftpWriteStringRaw(mOut, " SIZE");
        FtpUtil.ftpWriteStringRaw(mOut, " UTF8");
        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FEAT, ' ', "End");
    }

    private void handleOpts() {
        mSession.mFtpCmdArg.mArg = mSession.mFtpCmdArg.mArg.toUpperCase();
        if ("UTF8 ON".equals(mSession.mFtpCmdArg.mArg)) {
            mSession.isUTF8Required = true;
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_OPTSOK, ' ', "Always in UTF8 mode.");
        } else {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADOPTS, ' ', "Option not understood.");
        }
    }

    private void handleMkd() {
        Path rp = FtpUtil.ftpGetRealPath(mSession.userHomeDir, mSession.userCurrentDir, Path.valueOf(mSession.mFtpCmdArg.mArg));

        if (!checkFileAccess(rp, FilePermission.OPERATION_FILE_CREATE)) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOPERM, ' ', "Permission denied.");
            return;
        }

        int pathNode = mHost.getDirMapper().map(rp);
        if (pathNode == -1) {
            File f = rp.getFile();
            if (f.mkdirs()) {
                mSession.mFtpCmdArg.mArg = mSession.mFtpCmdArg.mArg.replace("\"", "\"\"");
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_MKDIROK, ' ', "\"" + mSession.mFtpCmdArg.mArg + "\" created.");
            } else {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Create directory operation failed.");
            }
        } else {
            try {
                mNodeController.chooseNode(pathNode);
                HostNodeSession nodeSession = mNodeController.getNodeSession();
                nodeSession.handleMkd(rp);
            } catch (IndexOutOfBoundsException ex) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Create directory operation failed.");
            }
        }
    }

    private void handleRmd() {
        Path rp = FtpUtil.ftpGetRealPath(mSession.userHomeDir, mSession.userCurrentDir, Path.valueOf(mSession.mFtpCmdArg.mArg));
        if (!checkFileAccess(rp, FilePermission.OPERATION_FILE_DELETE)) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOPERM, ' ', "Permission denied.");
            return;
        }

        int pathNode = mHost.getDirMapper().map(rp);
        if (pathNode == -1) {
            File f = rp.getFile();
            if (f.isDirectory() && f.delete()) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_RMDIROK, ' ', "Remove directory operation successful.");
            } else {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Remove directory operation failed.");
            }
        } else {
            try {
                mNodeController.chooseNode(pathNode);
                HostNodeSession nodeSession = mNodeController.getNodeSession();
                nodeSession.handleRmd(rp);
            } catch (IndexOutOfBoundsException ex) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Remove directory operation failed.");
            }
        }
    }

    private void handleDele() {
        Path rp = FtpUtil.ftpGetRealPath(mSession.userHomeDir, mSession.userCurrentDir, Path.valueOf(mSession.mFtpCmdArg.mArg));
        if (!checkFileAccess(rp, FilePermission.OPERATION_FILE_DELETE)) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOPERM, ' ', "Permission denied.");
            return;
        }

        int pathNode = mHost.getDirMapper().map(rp);
        if (pathNode == -1) {
            File f = rp.getFile();
            if (!f.isDirectory() && f.delete()) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_DELEOK, ' ', "Delete operation successful.");
            } else {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Delete operation failed.");
            }
        } else {
            try {
                mNodeController.chooseNode(pathNode);
                HostNodeSession nodeSession = mNodeController.getNodeSession();
                nodeSession.handleDele(rp);
            } catch (IndexOutOfBoundsException ex) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Delete operation failed.");
            }
        }
    }

    private void handleRnfr() {
        mSession.userRnfrFile = null;
        mSession.userRnfrFileNode = -1;
        Path rp = FtpUtil.ftpGetRealPath(mSession.userHomeDir, mSession.userCurrentDir, Path.valueOf(mSession.mFtpCmdArg.mArg));
        if (!checkFileAccess(rp, FilePermission.OPERATION_FILE_RENAME_FROM)) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOPERM, ' ', "Permission denied.");
            return;
        }

        int pathNode = mHost.getDirMapper().map(rp);
        if (pathNode == -1) {
            File f = rp.getFile();
            if (f.exists()) {
                mSession.userRnfrFile = f;
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_RNFROK, ' ', "Ready for RNTO.");
            } else {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "RNFR command failed.");
            }
        } else {
            try {
                mNodeController.chooseNode(pathNode);
                HostNodeSession nodeSession = mNodeController.getNodeSession();
                nodeSession.handleRnfr(rp);
            } catch (IndexOutOfBoundsException ex) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "RNFR command failed.");
            }
        }
    }

    private void handleRnto() {
        if (mSession.userRnfrFile == null && mSession.userRnfrFileNode == -1) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NEEDRNFR, ' ', "RNFR required first.");
        } else {
            Path rp = FtpUtil.ftpGetRealPath(mSession.userHomeDir, mSession.userCurrentDir, Path.valueOf(mSession.mFtpCmdArg.mArg));
            if (!checkFileAccess(rp, FilePermission.OPERATION_FILE_RENAME_TO)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOPERM, ' ', "Permission denied.");
                return;
            }

            int pathNode = mHost.getDirMapper().map(rp);
            if (pathNode != mSession.userCurrentDirNode) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Rename failed.");
                return;
            }

            if (pathNode == -1) {
                File f = rp.getFile();
                File from = mSession.userRnfrFile;
                mSession.userRnfrFile = null;
                if (from.renameTo(f)) {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_RENAMEOK, ' ', "Rename successful.");
                } else {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Rename failed.");
                }
            } else {
                try {
                    mNodeController.chooseNode(pathNode);
                    HostNodeSession nodeSession = mNodeController.getNodeSession();
                    nodeSession.handleRnto(rp);
                    mSession.userRnfrFileNode = -1;
                } catch (IndexOutOfBoundsException ex) {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Rename failed.");
                }
            }
        }
    }

    private void handleStat() {
        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_STATOK, '-', "FTP server status:");
        FtpUtil.ftpWriteStringRaw(mOut, "     Connected to " + mSession.userRemoteAddr);
        FtpUtil.ftpWriteStringRaw(mOut, "     Logged in as " + mSession.user);
        FtpUtil.ftpWriteStringRaw(mOut, "     TYPE: " + (mSession.isAscii ? "ASCII" : "BINARY"));
        FtpUtil.ftpWriteStringRaw(mOut, "     Control connection is plain text");
        FtpUtil.ftpWriteStringRaw(mOut, "     Data connections will be plain text");
        FtpUtil.ftpWriteStringRaw(mOut, "     At session startup, client count was " + mNumClients.intValue());
        FtpUtil.ftpWriteStringRaw(mOut, "     fdFTPd " + FtpMain.FDF_VER + " - a distributed ftp daemon");
        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_STATOK, ' ', "End");
    }

    private boolean handlePass() {
        if (mSession.mFtpCmdArg.mArg.isEmpty()) {//不允许空密码登陆
            return false;
        }
        if (mSession.userAnon && mTunables.hostAnonEnabled) {
            System.out.println("Anonmyous login with email " + mSession.mFtpCmdArg.mArg);
            //check for banned email
            if (mTunables.hostAnonDenyEmailEnabled) {
                for (String s : mTunables.hostAnonDenyEmail) {
                    if (s.equals(mSession.mFtpCmdArg.mArg)) {
                        return false;
                    }
                }
            }
            mSession.userHomeDir = mTunables.hostAnonHome; //加载anon设置
            mSession.userCurrentDir = mSession.userHomeDir;
            return true;
        }
        UserDefinition userDef = mTunables.hostUserDefinition.get(mSession.user);
        if (userDef != null && userDef.passwd.equals(mSession.mFtpCmdArg.mArg)) {
            //加载用户配置
            mSession.userHomeDir = userDef.home;
            mSession.userCurrentDir = mSession.userHomeDir;
            mSession.permission = userDef.permission;
            mSession.userCmdsAllowed = userDef.cmdsAllowed;
            mSession.userCmdsDenied = userDef.cmdsDenied;
            return true;
        }

        return false;
    }

    private void parseUsernamePassword() {
        while (readCmdArg()) {
            if ("USER".equals(mSession.mFtpCmdArg.mCmd)) {
                if (mSession.mFtpCmdArg.mArg.isEmpty()) {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_LOGINERR, ' ', "Error get USER.");
                    break;
                }
                mSession.user = mSession.mFtpCmdArg.mArg;
                mSession.mFtpCmdArg.mArg = mSession.mFtpCmdArg.mArg.toUpperCase();
                mSession.userAnon = "FTP".equals(mSession.mFtpCmdArg.mArg) || "ANONYMOUS".equals(mSession.mFtpCmdArg.mArg);
                if (mSession.userAnon && mTunables.hostAnonNoPassword) {
                    mSession.mFtpCmdArg.mArg = "<no password>";
                    if (handlePass()) {
                        postLogin();
                        break;
                    } else {
                        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_LOGINERR, ' ', "Login incorrect.");
                        if (!checkLoginFail()) {
                            break;
                        }
                    }
                } else {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_GIVEPWORD, ' ', "Please specify the password.");
                }
            } else if ("PASS".equals(mSession.mFtpCmdArg.mCmd)) {
                if (mSession.user.isEmpty()) {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NEEDUSER, ' ', "Login with USER first.");
                    continue;
                }
                if (handlePass()) {
                    postLogin();
                    break;
                } else {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_LOGINERR, ' ', "Login incorrect.");
                    if (!checkLoginFail()) {
                        break;
                    }
                }
            } else if ("QUIT".equals(mSession.mFtpCmdArg.mCmd)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_GOODBYE, ' ', "Goodbye.");
                break;
            } else if ("FEAT".equals(mSession.mFtpCmdArg.mCmd)) {
                handleFeatures();
            } else if ("OPTS".equals(mSession.mFtpCmdArg.mCmd)) {
                handleOpts();
            } else if (mSession.mFtpCmdArg.mCmd.isEmpty() && mSession.mFtpCmdArg.mArg.isEmpty()) {
            } else {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_LOGINERR, ' ', "Please login with USER and PASS.");
            }
        }
    }

    @Override
    public void run() {
        System.out.println("Session start!");
        while (true) {
            mNumClients.incrementAndGet();
            //开始监听
            try {
                mOut = new PrintWriter(new OutputStreamWriter(mIncoming.getOutputStream(), mTunables.hostRemoteCharset), true);
                mIn = new BufferedReader(new InputStreamReader(mIncoming.getInputStream(), mTunables.hostRemoteCharset));
            } catch (IOException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                break;
            }

            if (!checkLimits()) {
                break;
            }

            greeting();

            mSession = new HostSession();
            mSession.userRemoteAddr = FtpUtil.getSocketRemoteAddress(mIncoming);
            mSession.userNodeSession = new HostNodeSession[mTunables.hostNodes.length];
            mNodeController = new HostNodeController(this);

            parseUsernamePassword();

            break;
        }
        doClean();
        mNumClients.decrementAndGet();
        System.out.println("Session exit!");
    }

    private void postLogin() {
        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_LOGINOK, ' ', "Login successful.");
        while (readCmdArg()) {
            //检查命令是否在白名单或黑名单中
            boolean isCmdAllowed = false;
            //优先检查用户黑白名单
            if (mSession.userCmdsAllowed.length > 0) {
                for (String c : mSession.userCmdsAllowed) {
                    if (c.equals(mSession.mFtpCmdArg.mCmd)) {
                        isCmdAllowed = true;
                        break;
                    }
                }
            } else if (mTunables.hostCmdsAllowed.length > 0) {
                for (String c : mTunables.hostCmdsAllowed) {
                    if (c.equals(mSession.mFtpCmdArg.mCmd)) {
                        isCmdAllowed = true;
                        break;
                    }
                }
            } else {
                isCmdAllowed = true;
            }
            if (mSession.userCmdsDenied.length > 0) {
                for (String c : mSession.userCmdsDenied) {
                    if (c.equals(mSession.mFtpCmdArg.mCmd)) {
                        isCmdAllowed = false;
                        break;
                    }
                }
            } else if (mTunables.hostCmdsDenied.length > 0) {
                for (String c : mTunables.hostCmdsDenied) {
                    if (c.equals(mSession.mFtpCmdArg.mCmd)) {
                        isCmdAllowed = false;
                        break;
                    }
                }
            }

            if (!isCmdAllowed) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOPERM, ' ', "Permission denied.");
            } else if ("QUIT".equals(mSession.mFtpCmdArg.mCmd)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_GOODBYE, ' ', "Goodbye.");
                break;
            } else if ("PWD".equals(mSession.mFtpCmdArg.mCmd) || "XPWD".equals(mSession.mFtpCmdArg.mCmd)) {
                //路径中的双引号加倍
                String cwd = mSession.userCurrentDir.getRelativePath(mSession.userHomeDir).substring(1).replace("\"", "\"\"");
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_PWDOK, ' ', "\"" + cwd + "\"");
            } else if ("CWD".equals(mSession.mFtpCmdArg.mCmd) || "XCWD".equals(mSession.mFtpCmdArg.mCmd)) {
                handleCwd();
            } else if ("CDUP".equals(mSession.mFtpCmdArg.mCmd) || "XCUP".equals(mSession.mFtpCmdArg.mCmd)) {
                mSession.mFtpCmdArg.mArg = "../";
                handleCwd();
            } else if (("PASV".equals(mSession.mFtpCmdArg.mCmd) || "P@SW".equals(mSession.mFtpCmdArg.mCmd))
                    && mTunables.hostPasvEnabled) {
                handlePasv();
            } else if ("RETR".equals(mSession.mFtpCmdArg.mCmd) && mTunables.hostDownloadEnabled) {
                handleRetr();
            } else if ("NOOP".equals(mSession.mFtpCmdArg.mCmd)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOOPOK, ' ', "NOOP ok.");
            } else if ("SYST".equals(mSession.mFtpCmdArg.mCmd)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_SYSTOK, ' ', "UNIX Type: L8");
                //FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_SYSTOK, ' ', "Windows_NT version 5.0");
            } else if ("LIST".equals(mSession.mFtpCmdArg.mCmd) && mTunables.hostDirListEnabled) {
                handleDirCommon(true, false);
            } else if ("TYPE".equals(mSession.mFtpCmdArg.mCmd)) {
                if ("I".equals(mSession.mFtpCmdArg.mArg) || "L8".equals(mSession.mFtpCmdArg.mArg) || "L 8".equals(mSession.mFtpCmdArg.mArg)) {
                    mSession.isAscii = false;
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_TYPEOK, ' ', "Switching to Binary mode.");
                } else if ("A".equals(mSession.mFtpCmdArg.mArg) || "A N".equals(mSession.mFtpCmdArg.mArg)) {
                    mSession.isAscii = true;
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_TYPEOK, ' ', "Switching to ASCII mode.");
                } else {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADCMD, ' ', "Unrecognised TYPE command.");
                }
            } else if ("PORT".equals(mSession.mFtpCmdArg.mCmd) && mTunables.hostPortEnabled) {
                handlePort();
            } else if ("STOR".equals(mSession.mFtpCmdArg.mCmd) && mTunables.hostWriteEnabled
                    && (mTunables.hostAnonUploadEnabled || !mSession.userAnon)) {
                handleUploadCommon(false, false);
            } else if (("MKD".equals(mSession.mFtpCmdArg.mCmd) || "XMKD".equals(mSession.mFtpCmdArg.mCmd))
                    && mTunables.hostWriteEnabled
                    && (mTunables.hostAnonMkdirWriteEnabled || !mSession.userAnon)) {
                handleMkd();
            } else if (("RMD".equals(mSession.mFtpCmdArg.mCmd) || "XRMD".equals(mSession.mFtpCmdArg.mCmd))
                    && mTunables.hostWriteEnabled
                    && (mTunables.hostAnonOtherWriteEnabled || !mSession.userAnon)) {
                handleRmd();
            } else if ("DELE".equals(mSession.mFtpCmdArg.mCmd)
                    && mTunables.hostWriteEnabled
                    && (mTunables.hostAnonOtherWriteEnabled || !mSession.userAnon)) {
                handleDele();
            } else if ("REST".equals(mSession.mFtpCmdArg.mCmd)) {
                long pos = 0;
                try {
                    pos = Long.valueOf(mSession.mFtpCmdArg.mArg);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
                mSession.userFileRestartOffset = pos;
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_RESTOK, ' ', "Restart position accepted (" + pos + ").");
            } else if ("RNFR".equals(mSession.mFtpCmdArg.mCmd)
                    && mTunables.hostWriteEnabled
                    && (mTunables.hostAnonOtherWriteEnabled || !mSession.userAnon)) {
                handleRnfr();
            } else if ("RNTO".equals(mSession.mFtpCmdArg.mCmd)
                    && mTunables.hostWriteEnabled
                    && (mTunables.hostAnonOtherWriteEnabled || !mSession.userAnon)) {
                handleRnto();
            } else if ("NLST".equals(mSession.mFtpCmdArg.mCmd) && mTunables.hostDirListEnabled) {
                handleDirCommon(false, false);
            } else if ("SIZE".equals(mSession.mFtpCmdArg.mCmd)) {
                handleSize();
            } else if ("SITE".equals(mSession.mFtpCmdArg.mCmd) && !mSession.userAnon) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_COMMANDNOTIMPL, ' ', "SITE not implemented.");
            } else if ("ABOR".equals(mSession.mFtpCmdArg.mCmd) || "\377\364\377\362ABOR".equals(mSession.mFtpCmdArg.mCmd)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_ABOR_NOCONN, ' ', "No transfer to ABOR.");
            } else if ("APPE".equals(mSession.mFtpCmdArg.mCmd)
                    && mTunables.hostWriteEnabled
                    && (mTunables.hostAnonOtherWriteEnabled || !mSession.userAnon)) {
                handleUploadCommon(true, false);
            } else if ("MDTM".equals(mSession.mFtpCmdArg.mCmd)) {
                handleMdtm();
            } else if ("STRU".equals(mSession.mFtpCmdArg.mCmd)) {
                mSession.mFtpCmdArg.mArg = mSession.mFtpCmdArg.mArg.toUpperCase();
                if ("F".equals(mSession.mFtpCmdArg.mArg)) {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_STRUOK, ' ', "Structure set to F.");
                } else {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSTRU, ' ', "Bad STRU command.");
                }
            } else if ("MODE".equals(mSession.mFtpCmdArg.mCmd)) {
                mSession.mFtpCmdArg.mArg = mSession.mFtpCmdArg.mArg.toUpperCase();
                if ("S".equals(mSession.mFtpCmdArg.mArg)) {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_MODEOK, ' ', "Mode set to S.");
                } else {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADMODE, ' ', "Bad MODE command.");
                }
            } else if ("STOU".equals(mSession.mFtpCmdArg.mCmd)
                    && mTunables.hostWriteEnabled
                    && (mTunables.hostAnonUploadEnabled || !mSession.userAnon)) {
                handleUploadCommon(false, true);
            } else if ("ALLO".equals(mSession.mFtpCmdArg.mCmd)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_ALLOOK, ' ', "ALLO command ignored.");
            } else if ("REIN".equals(mSession.mFtpCmdArg.mCmd)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_COMMANDNOTIMPL, ' ', "REIN not implemented.");
            } else if ("ACCT".equals(mSession.mFtpCmdArg.mCmd)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_COMMANDNOTIMPL, ' ', "ACCT not implemented.");
            } else if ("SMNT".equals(mSession.mFtpCmdArg.mCmd)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_COMMANDNOTIMPL, ' ', "SMNT not implemented.");
            } else if ("FEAT".equals(mSession.mFtpCmdArg.mCmd)) {
                handleFeatures();
            } else if ("OPTS".equals(mSession.mFtpCmdArg.mCmd)) {
                handleOpts();
            } else if ("USER".equals(mSession.mFtpCmdArg.mCmd)) {
                if (mSession.userAnon) {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_LOGINERR, ' ', "Can't change from guest user.");
                } else if (mSession.user.equals(mSession.mFtpCmdArg.mArg)) {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_GIVEPWORD, ' ', "Any password will do.");
                } else {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_LOGINERR, ' ', "Can't change to another user.");
                }
            } else if ("STAT".equals(mSession.mFtpCmdArg.mCmd) && mSession.mFtpCmdArg.mArg.isEmpty()) {
                handleStat();
            } else if ("STAT".equals(mSession.mFtpCmdArg.mCmd)) {
                handleDirCommon(true, true);
            } else if ("PASS".equals(mSession.mFtpCmdArg.mCmd)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_LOGINOK, ' ', "Already logged in.");
            } else if ("PASV".equals(mSession.mFtpCmdArg.mCmd) || "PORT".equals(mSession.mFtpCmdArg.mCmd) ||
                    "STOR".equals(mSession.mFtpCmdArg.mCmd) || "MKD".equals(mSession.mFtpCmdArg.mCmd) ||
                    "XMKD".equals(mSession.mFtpCmdArg.mCmd) || "RMD".equals(mSession.mFtpCmdArg.mCmd) ||
                    "XRMD".equals(mSession.mFtpCmdArg.mCmd) || "DELE".equals(mSession.mFtpCmdArg.mCmd) ||
                    "RNFR".equals(mSession.mFtpCmdArg.mCmd) || "RNTO".equals(mSession.mFtpCmdArg.mCmd) ||
                    "SITE".equals(mSession.mFtpCmdArg.mCmd) || "APPE".equals(mSession.mFtpCmdArg.mCmd) ||
                    "EPSV".equals(mSession.mFtpCmdArg.mCmd) || "EPRT".equals(mSession.mFtpCmdArg.mCmd) ||
                    "RETR".equals(mSession.mFtpCmdArg.mCmd) || "LIST".equals(mSession.mFtpCmdArg.mCmd) ||
                    "NLST".equals(mSession.mFtpCmdArg.mCmd) || "STOU".equals(mSession.mFtpCmdArg.mCmd) ||
                    "ALLO".equals(mSession.mFtpCmdArg.mCmd) || "REIN".equals(mSession.mFtpCmdArg.mCmd) ||
                    "ACCT".equals(mSession.mFtpCmdArg.mCmd) || "SMNT".equals(mSession.mFtpCmdArg.mCmd) ||
                    "FEAT".equals(mSession.mFtpCmdArg.mCmd) || "OPTS".equals(mSession.mFtpCmdArg.mCmd) ||
                    "STAT".equals(mSession.mFtpCmdArg.mCmd) || "PBSZ".equals(mSession.mFtpCmdArg.mCmd) ||
                    "PROT".equals(mSession.mFtpCmdArg.mCmd)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOPERM, ' ', "Permission denied.");
            } else if (mSession.mFtpCmdArg.mCmd.isEmpty() && mSession.mFtpCmdArg.mArg.isEmpty()) {

            } else {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADCMD, ' ', "Unknown command.");
            }
        }
        System.out.println("Oops!");
    }
}
