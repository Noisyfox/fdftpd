package org.foxteam.noisyfox.fdf.Node;

import org.foxteam.noisyfox.fdf.FilePermission;
import org.foxteam.noisyfox.fdf.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-25
 * Time: 下午9:07
 * To change this template use File | Settings | File Templates.
 */
public class NodeServant extends Thread {

    private NodeSession mSession;

    private final Socket mSocket;
    private final PrintWriter mWriter;
    private final BufferedReader mReader;
    private final NodeDirectoryMapper mDirectoryMapper;
    private final NodeCenter mNodeCenter;

    private RequestCmdArg mHostCmdArg = new RequestCmdArg();
    private int mMidwayStatusCode = -1;//储存所有不是在handle*()函数中产生的状态信息
    private Object[] mMidwayStatusMsg = null;

    private FtpBridge mCurrentBridge = null;
    private FtpBridge.OnBridgeWorkFinishListener mBridgeFinishListener = new FtpBridge.OnBridgeWorkFinishListener() {
        @Override
        public void onWorkFinish() {
            ioCloseConnection();
            mCurrentBridge = null;
        }
    };

    public NodeServant(NodeCenter nodeCenter, Socket socket) throws IOException {
        mNodeCenter = nodeCenter;
        mDirectoryMapper = mNodeCenter.mDirectoryMapper;
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

    @Override
    public void run() {
        System.out.println("Node servant started!");
        mSession = new NodeSession();
        servantLoop();
        System.out.println("Node servant exit!");
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

        mMidwayStatusCode = FtpCodes.FTP_DATACONN;
        mMidwayStatusMsg = new Object[]{' ', msg};

        return true;
    }

    private void ioCloseConnection() {

        if (mSession.userDataTransferReaderAscii != null) try {
            mSession.userDataTransferReaderAscii.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (mSession.userDataTransferReaderBinary != null) try {
            mSession.userDataTransferReaderBinary.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (mSession.userDataTransferWriterAscii != null) mSession.userDataTransferWriterAscii.close();
        if (mSession.userDataTransferWriterBinary != null) try {
            mSession.userDataTransferWriterBinary.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (mSession.userDataTransferSocket != null) try {
            mSession.userDataTransferSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
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
                mMidwayStatusCode = FtpCodes.FTP_BADSENDCONN;
                mMidwayStatusMsg = new Object[]{' ', "Security: Bad IP connecting."};
                try {
                    tempSocket.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                return false;
            }
            long maxRate = mSession.userAnon ? mNodeCenter.mTunables.hostAnonTransferRateMax : mNodeCenter.mTunables.hostTransferRateMax;
            String charSet = mSession.asciiCharset; //使用默认编码
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

            mMidwayStatusCode = FtpCodes.FTP_BADSENDCONN;
            mMidwayStatusMsg = new Object[]{' ', "Failed to establish connection."};
            //FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDCONN, ' ', "Failed to establish connection.");

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
            long maxRate = mSession.userAnon ? mNodeCenter.mTunables.hostAnonTransferRateMax : mNodeCenter.mTunables.hostTransferRateMax;
            tempSocket = new Socket(mSession.userPortSocketAddr, mSession.userPortSocketPort);
            String charSet = mSession.asciiCharset; //使用默认编码
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
            mMidwayStatusCode = FtpCodes.FTP_BADSENDCONN;
            mMidwayStatusMsg = new Object[]{' ', "Failed to establish connection."};
            //FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDCONN, ' ', "Failed to establish connection.");

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

    private boolean readCmdArg() {
        return FtpUtil.readCmdArg(mSocket, mReader, mHostCmdArg, 0, "Host");
    }

    private void cleanPasv() {
        if (mSession.userPasvSocketServer != null) {
            try {
                mSession.userPasvSocketServer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mSession.userPasvSocketServer = null;
    }

    private void cleanPort() {
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
        if (!isPasvActivate() && !isPortActivate()) {
            mMidwayStatusCode = FtpCodes.FTP_BADSENDCONN;
            mMidwayStatusMsg = new Object[]{' ', "Use PORT or PASV first."};
            return false;
        }
        return true;
    }

    private void servantLoop() {
        while (readCmdArg()) {
            if ("NOOP".equals(mHostCmdArg.mCmd)) {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_NOOPOK, ' ', "NOOP ok.");
            } else if ("UNAME".equals(mHostCmdArg.mCmd)) {
                mSession.user = mHostCmdArg.mArg;
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.HOST_INFOOK, ' ', "Remote user updated:", mSession.user);
            } else if ("ANON".equals(mHostCmdArg.mCmd)) {
                mSession.userAnon = true;
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.HOST_INFOOK, ' ', "Remote user is set to anon.");
            } else if ("RADDR".equals(mHostCmdArg.mCmd)) {
                mSession.userRemoteAddr = mHostCmdArg.mArg;
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.HOST_INFOOK, ' ', "Remote address updated:", mSession.userRemoteAddr);
            } else if ("PERD".equals(mHostCmdArg.mCmd)) {
                handlePermission(true);
            } else if ("PERF".equals(mHostCmdArg.mCmd)) {
                handlePermission(false);
            } else if ("REVE".equals(mHostCmdArg.mCmd)) {
                handleBridge(true);
            } else if ("SEND".equals(mHostCmdArg.mCmd)) {
                handleBridge(false);
            } else if ("CWD".equals(mHostCmdArg.mCmd)) {
                handleCwd();
            } else if ("PASV".equals(mHostCmdArg.mCmd)) {
                handlePasv();
            } else if ("PORT".equals(mHostCmdArg.mCmd)) {
                handlePort();
            } else if ("SIZE".equals(mHostCmdArg.mCmd)) {
                handleSize();
            } else if ("STAT".equals(mHostCmdArg.mCmd)) {
                handleStat();
            } else if ("LIST".equals(mHostCmdArg.mCmd)) {
                handleList();
            } else if ("MDTM".equals(mHostCmdArg.mCmd)) {
                handleMdtm();
            } else if ("MKD".equals(mHostCmdArg.mCmd)) {
                handleMkd();
            } else if ("RMD".equals(mHostCmdArg.mCmd)) {
                handleRmd();
            } else if ("DELE".equals(mHostCmdArg.mCmd)) {
                handleDele();
            } else if ("RNFR".equals(mHostCmdArg.mCmd)) {
                handleRnfr();
            } else if ("RNTO".equals(mHostCmdArg.mCmd)) {
                handleRnto();
            } else if ("TYPE".equals(mHostCmdArg.mCmd)) {
                if ("I".equals(mHostCmdArg.mArg)) {
                    mSession.isAscii = false;
                    FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_TYPEOK, ' ', "Switching to Binary mode.");
                } else if ("A".equals(mHostCmdArg.mArg)) {
                    mSession.isAscii = true;
                    FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_TYPEOK, ' ', "Switching to ASCII mode.");
                } else {
                    FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADCMD, ' ', "Unrecognised TYPE command.");
                }
            } else if ("REST".equals(mHostCmdArg.mCmd)) {
                long pos = 0;
                try {
                    pos = Long.valueOf(mHostCmdArg.mArg);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
                mSession.userFileRestartOffset = pos;
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_RESTOK, ' ', "Restart position accepted (" + pos + ").");
            } else if ("CSET".equals(mHostCmdArg.mCmd)) {
                mSession.asciiCharset = mHostCmdArg.mArg;
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.NODE_CHARSETOK, ' ', "Charset set to " + mSession.asciiCharset);
            } else if ("UQUE".equals(mHostCmdArg.mCmd)) {
                handleUque();
            } else if ("RETR".equals(mHostCmdArg.mCmd)) {
                handleRetr(Path.valueOf(mHostCmdArg.mArg));
            } else if ("STOR".equals(mHostCmdArg.mCmd)) {
                handleStor();
            } else if ("QUIT".equals(mHostCmdArg.mCmd)) {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_GOODBYE, ' ', "Goodbye.");
                break;
            } else {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADCMD, ' ', "Unknown command.");
            }
        }
    }

    private void handlePermission(boolean isDir) {
        String[] val = mHostCmdArg.mArg.split("::");
        if (val.length < 2) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADCMD, ' ', "Illegal file permission:", mHostCmdArg.mArg);
            return;
        }
        Path fileP = Path.valueOf(val[0]);
        Path mappedPath = mDirectoryMapper.map(fileP);
        if (mappedPath != null) {
            fileP = mappedPath;
        }
        int pCode;
        try {
            pCode = Integer.parseInt(val[1]);
        } catch (NumberFormatException e) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADCMD, ' ', "Illegal file permission:", mHostCmdArg.mArg);
            return;
        }
        char c = '-';
        if ((pCode & FilePermission.ACCESS_INHERIT) != 0) {
            c = '*';
        } else if ((pCode & FilePermission.ACCESS_DIRECTORY) != 0) {
            c = '?';
        }
        mSession.permission.addPermission(isDir, (pCode & FilePermission.ACCESS_READ) != 0,
                (pCode & FilePermission.ACCESS_WRITE) != 0,
                (pCode & FilePermission.ACCESS_EXECUTE) != 0, fileP, c);
        FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.HOST_INFOOK, ' ', "Add file permission:", mHostCmdArg.mArg);

    }

    private void handleCwd() {
        Path rp = mDirectoryMapper.map(Path.valueOf(mHostCmdArg.mArg));
        if (rp == null) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Failed to change directory.");
        } else {
            File f = rp.getFile();
            if (!f.exists() || !f.isDirectory()) {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Failed to change directory.");
            } else {
                mSession.userCurrentDir = rp;
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_CWDOK, ' ', "Directory successfully changed.");
            }
        }
    }

    private void handlePasv() {
        cleanPasv();
        cleanPort();

        //尝试开启端口监听
        ServerSocket ss = FtpUtil.openRandomPort();
        if (ss == null) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADCMD, ' ',
                    "Enter Passive Mode Failed.");
            return;
        }
        int selectedPort = ss.getLocalPort();

        String address = mSocket.getLocalAddress().toString().replace("/", "").replace(".", ",");
        FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_PASVOK, ' ',
                "Entering Passive Mode (", address, ",", (selectedPort >> 8), ",", (selectedPort & 0xFF), ").");
        //储存pasv监听器
        mSession.userPasvSocketServer = ss;
    }

    private void handlePort() {
        cleanPasv();
        cleanPort();

        String[] values = mHostCmdArg.mArg.split(",");
        if (values.length != 6) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADCMD, ' ', "Illegal PORT command.");
            return;
        }
        int sockPort;
        try {
            sockPort = Integer.valueOf(values[4]) << 8 | Integer.valueOf(values[5]);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADCMD, ' ', "Illegal PORT command.");
            return;
        }

        String sockAddr = String.format("%s.%s.%s.%s", values);
        System.out.println(sockAddr + ":" + sockPort);
        /* SECURITY:
        * 1) Reject requests not connecting to the control socket IP
        * 2) Reject connects to privileged ports
        */
        if (sockPort < FtpMain.IPPORT_RESERVED) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADCMD, ' ', "Illegal PORT command.");
            return;
        }

        mSession.userPortSocketAddr = sockAddr;
        mSession.userPortSocketPort = sockPort;

        FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_PORTOK, ' ', "PORT command successful. Consider using PASV.");
    }

    private void handleSize() {
        Path rp = mDirectoryMapper.map(Path.valueOf(mHostCmdArg.mArg));
        if (rp == null) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Could not get file size.");
        } else {
            File f = rp.getFile();
            if (!f.exists() || f.isDirectory()) {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Could not get file size.");
                return;
            }
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_SIZEOK, ' ', String.valueOf(f.length()));
        }
    }

    private void handleMdtm() {
        String[] val = mHostCmdArg.mArg.split("::");
        if (val.length < 2) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADCMD, ' ', "Illegal MDTM command");
            return;
        }
        long mTime;
        try {
            mTime = Long.parseLong(val[1]);
        } catch (NumberFormatException ex) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADCMD, ' ', "Illegal MDTM command");
            return;
        }

        Path rp = mDirectoryMapper.map(Path.valueOf(val[0]));
        if (rp == null) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ',
                    mTime >= 0 ? "Could not set file modification time." : "Could not get file modification time.");
        } else {
            File f = rp.getFile();
            if (!f.exists() || f.isDirectory()) {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ',
                        mTime >= 0 ? "Could not set file modification time." : "Could not get file modification time.");
                return;
            }
            if (mTime >= 0) {
                try {
                    if (f.setLastModified(mTime)) {
                        FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_MDTMOK, ' ', "File modification time set.");
                    } else {
                        FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Could not set file modification time.");
                    }
                } catch (Exception e) {
                    FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Could not set file modification time.");
                }
            } else {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_MDTMOK, ' ', FtpUtil.dateFormatMdtm.format(new Date(f.lastModified())));
            }
        }
    }

    private void handleMkd() {
        Path rp = mDirectoryMapper.map(Path.valueOf(mHostCmdArg.mArg));
        if (rp == null) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Create directory operation failed.");
        } else {
            File f = rp.getFile();
            if (f.mkdirs()) {
                mHostCmdArg.mArg = mHostCmdArg.mArg.replace("\"", "\"\"");
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_MKDIROK, ' ', "\"" + mHostCmdArg.mArg + "\" created.");
            } else {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Create directory operation failed.");
            }
        }
    }

    private void handleRmd() {
        Path rp = mDirectoryMapper.map(Path.valueOf(mHostCmdArg.mArg));
        if (rp == null) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Remove directory operation failed.");
        } else {
            File f = rp.getFile();
            if (f.isDirectory() && f.delete()) {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_RMDIROK, ' ', "Remove directory operation successful.");
            } else {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Remove directory operation failed.");
            }
        }
    }

    private void handleDele() {
        Path rp = mDirectoryMapper.map(Path.valueOf(mHostCmdArg.mArg));
        if (rp == null) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Delete operation failed.");
        } else {
            File f = rp.getFile();
            if (!f.isDirectory() && f.delete()) {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_DELEOK, ' ', "Delete operation successful.");
            } else {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Delete operation failed.");
            }
        }
    }

    private void handleRnfr() {
        mSession.userRnfrFile = null;

        Path rp = mDirectoryMapper.map(Path.valueOf(mHostCmdArg.mArg));
        if (rp == null) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "RNFR command failed.");
        } else {
            File f = rp.getFile();
            if (f.exists()) {
                mSession.userRnfrFile = f;
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_RNFROK, ' ', "Ready for RNTO.");
            } else {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "RNFR command failed.");
            }
        }
    }

    private void handleRnto() {
        if (mSession.userRnfrFile == null) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_NEEDRNFR, ' ', "RNFR required first.");
        } else {
            Path rp = mDirectoryMapper.map(Path.valueOf(mHostCmdArg.mArg));
            if (rp == null) {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Rename failed.");
            } else {
                File f = rp.getFile();
                File from = mSession.userRnfrFile;
                mSession.userRnfrFile = null;
                if (from.renameTo(f)) {
                    FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_RENAMEOK, ' ', "Rename successful.");
                } else {
                    FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Rename failed.");
                }
            }
        }
    }

    private void handleStat() {
        String[] val = mHostCmdArg.mArg.split("::");
        if (val.length < 2) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADCMD, ' ', "Illegal STAT command.");
            return;
        }

        Path rp = mDirectoryMapper.map(Path.valueOf(val[0]));
        if (rp == null) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Could not get file stat");
        } else {
            FtpUtil.outPutDir(mWriter, rp, val[1],
                    true, mSession.userAnon, mNodeCenter.mTunables, mSession.permission,
                    FtpCodes.NODE_OPSMSG + " " + FtpCodes.FTP_STATFILE_OK + " ");
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_STATFILE_OK, ' ', "STAT finish.");
        }
    }

    private void handleUque() {
        Path rp = mDirectoryMapper.map(Path.valueOf(mHostCmdArg.mArg));
        if (rp == null) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Path not found.");
        } else {
            String name = FtpUtil.getUniqueFileName(rp);
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.NODE_UNIQUEOK, ' ', name);
        }
    }

    private void handleList() {
        String[] val = mHostCmdArg.mArg.split("::");
        if (val.length < 3) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADCMD, ' ', "Illegal LIST command.");
            return;
        }

        Path rp = mDirectoryMapper.map(Path.valueOf(val[0]));
        if (rp == null) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Could not get file stat");
        } else {
            //开始传输数据
            if (!ioOpenConnection("Here comes the directory listing.")) {
                cleanPasv();
                cleanPort();
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Failed to list directory.");
                return;
            }

            //开始列举目录
            boolean transferSuccess = FtpUtil.outPutDir(mSession.userDataTransferWriterAscii, rp, val[1],
                    val[2].equals("1"), mSession.userAnon, mNodeCenter.mTunables, mSession.permission, "");

            ioCloseConnection();

            if (!transferSuccess) {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADSENDNET, ' ', "Failure writing network stream.");
            } else {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_TRANSFEROK, ' ', "Directory send OK.");
            }

            cleanPasv();
            cleanPort();
        }
    }

    private void handleDownloadAsciiCommon(Path path) {
        File f = path.getFile();
        BufferedReader br;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(f),
                    mSession.asciiCharset));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Failed to open file.");
            return;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Failed to open file.");
            return;
        }

        if (!ioOpenConnection("")) {
            cleanPasv();
            cleanPort();
            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Failed to establish connection.");
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
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADSENDFILE, ' ', "Failure reading local file.");
        } else if (!transferSuccess) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADSENDNET, ' ', "Failure writing network stream.");
        } else {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_TRANSFEROK, ' ', "Transfer complete.");
        }

        ioCloseConnection();

        cleanPasv();
        cleanPort();

        try {
            br.close();
        } catch (IOException ignored) {
        }
    }

    private void handleDownloadBinaryCommon(Path path) {
        File f = path.getFile();
        BufferedInputStream bis;
        try {
            bis = new BufferedInputStream(new FileInputStream(f));
            try {
                bis.skip(mSession.userFileRestartOffset);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Failed to open file.");
            return;
        }

        if (!ioOpenConnection("")) {
            cleanPasv();
            cleanPort();
            try {
                bis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Failed to establish connection.");
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
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADSENDFILE, ' ', "Failure reading local file.");
        } else if (!transferSuccess) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADSENDNET, ' ', "Failure writing network stream.");
        } else {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_TRANSFEROK, ' ', "Transfer complete.");
        }

        ioCloseConnection();

        cleanPasv();
        cleanPort();

        try {
            bis.close();
        } catch (IOException ignored) {
        }
    }

    private void handleRetr(Path path) {
        Path rp = mDirectoryMapper.map(path);
        if (rp == null) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Could not read file.");
        } else {
            if (mSession.isAscii) {
                handleDownloadAsciiCommon(rp);
            } else {
                handleDownloadBinaryCommon(rp);
            }
        }
    }

    private void handleStor() {
        String[] val = mHostCmdArg.mArg.split("::");
        if (val.length < 2) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADCMD, ' ', "Illegal STOR command.");
            return;
        }

        Path rp = mDirectoryMapper.map(Path.valueOf(val[0]));
        if (rp == null) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Could not write file.");
        } else {
            boolean isAppend = val[1].equals("1");
            long offset = mSession.userFileRestartOffset;
            boolean ascii = mSession.isAscii;

            File f = rp.getFile();
            RandomAccessFile raf;
            try {
                raf = new RandomAccessFile(f, "rw");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_UPLOADFAIL, ' ', "Could not create file or open exist file.");
                return;
            }

            try {
                if (!isAppend && offset != 0) {
                    raf.seek(offset);
                } else if (isAppend) {
                    raf.seek(raf.length());
                } else {
                    raf.setLength(0);//清除已有内容
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (!ioOpenConnection("")) {
                cleanPasv();
                cleanPort();
                try {
                    raf.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADSENDNET, ' ', "Failed to establish connection.");
                return;
            }

            //接收数据
            boolean transferSuccess = true;
            boolean fileWriteSuccess = true;
            if (ascii) {
                String charSet = mSession.asciiCharset;
                String line;
                try {
                    while ((line = mSession.userDataTransferReaderAscii.readLine()) != null) {
                        byte[] bytes = line.getBytes(charSet);
                        try {
                            raf.write(bytes);
                        } catch (IOException e) {
                            fileWriteSuccess = false;
                        }
                    }
                } catch (IOException e) {
                    transferSuccess = false;
                }
            } else {
                byte[] bytes = new byte[10240];
                int size;
                try {
                    while ((size = mSession.userDataTransferReaderBinary.read(bytes)) != -1) {
                        try {
                            raf.write(bytes, 0, size);
                        } catch (IOException e) {
                            fileWriteSuccess = false;
                        }
                    }
                } catch (IOException e) {
                    transferSuccess = false;
                }
            }

            if (!fileWriteSuccess) {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADSENDFILE, ' ', "Failure writing to local file.");
            } else if (!transferSuccess) {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADSENDNET, ' ', "Failure reading network stream.");
            } else {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_TRANSFEROK, ' ', "Transfer complete.");
            }

            ioCloseConnection();
            cleanPasv();
            cleanPort();
            try {
                raf.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void handleBridge(boolean isReceive) {
        if (mCurrentBridge != null && !mCurrentBridge.isDead()) {
            mCurrentBridge.kill();
        }

        if (!checkDataTransferOk()) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, mMidwayStatusCode, mMidwayStatusMsg);
            return;
        }

        //Open data transfer stream
        if (!ioOpenConnection("")) {
            cleanPasv();
            cleanPort();
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.NODE_BADBRIDGE, ' ',
                    "Open bridge failed.");
            return;
        }

        //Open bridge
        ServerSocket listener = FtpUtil.openRandomPort();
        if (listener == null) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.NODE_BADBRIDGE, ' ',
                    "Open bridge failed.");
            return;
        }
        int port = listener.getLocalPort();
        mCurrentBridge = new FtpBridge(listener, mHostCmdArg.mArg, mBridgeFinishListener);

        if (isReceive) {
            mCurrentBridge.startForReceiving(mSession.userDataTransferWriterBinary);
        } else {
            mCurrentBridge.startForSending(mSession.userDataTransferReaderBinary);
        }

        FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.NODE_BRIDGEOK, ' ',
                port);
    }

}
