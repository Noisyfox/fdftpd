package org.foxteam.noisyfox.fdf.Node;

import org.foxteam.noisyfox.fdf.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

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
        //FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_DATACONN, ' ', msg);

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
                mMidwayStatusCode = FtpCodes.FTP_BADSENDCONN;
                mMidwayStatusMsg = new Object[]{' ', "Security: Bad IP connecting."};
                //FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDCONN, ' ', "Security: Bad IP connecting.");
                try {
                    tempSocket.close();
                } catch (IOException e1) {
                    e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
                return false;
            }
            long maxRate = mSession.userAnon ? mNodeCenter.mTunables.hostAnonTransferRateMax : mNodeCenter.mTunables.hostTransferRateMax;
            String charSet = mSession.isUTF8Required ? "UTF-8" : mNodeCenter.mTunables.hostDefaultTransferCharset; //使用默认编码
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
            String charSet = mSession.isUTF8Required ? "UTF-8" : mNodeCenter.mTunables.hostDefaultTransferCharset; //使用默认编码
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
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
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
            } else if ("CWD".equals(mHostCmdArg.mCmd)) {
                handleCwd();
            } else if ("PASV".equals(mHostCmdArg.mCmd)) {
                handlePasv();
            } else if ("PORT".equals(mHostCmdArg.mCmd)) {
                handlePort();
            } else if ("SIZE".equals(mHostCmdArg.mCmd)) {
                handleSize();
            } else {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADCMD, ' ', "Unknown command.");
            }
        }
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
        if (!sockAddr.equals(mSession.userRemoteAddr) || sockPort < FtpMain.IPPORT_RESERVED) {
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

}
