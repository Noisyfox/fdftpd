package org.foxteam.nosiyfox.fdf.Host;

import org.foxteam.nosiyfox.fdf.FtpCodes;
import org.foxteam.nosiyfox.fdf.FtpMain;
import org.foxteam.nosiyfox.fdf.FtpUtil;
import org.foxteam.nosiyfox.fdf.Tunables;

import java.io.*;
import java.net.Socket;
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

    protected final Tunables mTunables;
    protected final Socket mIncoming;
    protected PrintWriter mOut;
    protected BufferedReader mIn;

    protected FtpSession mSession;

    protected HostServant(Tunables tunables, Socket socket) {
        mTunables = tunables;
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

        if (mSession.userDataTransferReader != null) try {
            mSession.userDataTransferReader.close();
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
        mSession.userDataTransferReader = null;
        mSession.userDataTransferWriterAscii = null;
        mSession.userDataTransferWriterBinary = null;
    }

    private boolean ioStartConnectionPasv() {
        return false;
    }

    private boolean ioStartConnectionPort() {
        Socket tempSocket = null;
        BufferedReader tempReader = null;
        PrintWriter tempWriterAscii = null;
        BufferedWriter tempWriterBinary = null;
        try {
            tempSocket = new Socket(mSession.userPortSocketAddr, mSession.userPortSocketPort);
            String charSet = mSession.isUTF8Required ? "UTF-8" : mTunables.hostDefaultTransferCharset; //使用默认编码
            tempReader = new BufferedReader(new InputStreamReader(tempSocket.getInputStream(), charSet));
            OutputStream os = tempSocket.getOutputStream();
            tempWriterBinary = new BufferedWriter(new OutputStreamWriter(os, charSet));
            tempWriterAscii = new PrintWriter(tempWriterBinary, true);
        } catch (IOException e) {
            e.printStackTrace();
            //clean
            if (tempReader != null) try {
                tempReader.close();
            } catch (IOException e1) {
                e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
            if (tempWriterAscii != null) tempWriterAscii.close();
            if (tempWriterBinary != null) try {
                tempWriterBinary.close();
            } catch (IOException e1) {
                e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
            if (tempSocket != null) try {
                tempSocket.close();
            } catch (IOException e1) {
                e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDCONN, ' ', "Failed to establish connection.");

            return false;
        }
        mSession.userDataTransferSocket = tempSocket;
        mSession.userDataTransferReader = tempReader;
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
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        mIn = null;

        try {
            mIncoming.close();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

    }

    private boolean readCmdArg() {
        mSession.ftpCmd = "";
        mSession.ftpArg = "";
        try {
            String line = mIn.readLine();
            if (line != null) {
                int i = line.indexOf(' ');
                if (i != -1) {
                    mSession.ftpCmd = line.substring(0, i).trim().toUpperCase();
                    mSession.ftpArg = line.substring(i).trim();
                } else {
                    mSession.ftpCmd = line;
                }
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return false;
    }

    private boolean checkLimits() {
        if (mTunables.hostMaxClients > 0 && mNumClients.get() > mTunables.hostMaxClients) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_TOO_MANY_USERS, ' ', "There are too many connected users, please try later.");
            return false;
        }
        return true;
    }

    private void greeting() {
        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_GREET, ' ', "(fdFTPd " + FtpMain.FDF_VER + " )");
    }

    private boolean checkLoginFail() {
        mSession.loginFails++;
        return mSession.loginFails <= mTunables.hostMaxLoginFails;
    }

    private boolean checkFileAccess(String path) {
        //验证是否为home的子目录并且目录是否可读
        return path.startsWith(mSession.userHomeDir) && new File(path).canRead();

    }

    private void handleCwd() {
        //获取真实路径
        String rp = FtpUtil.ftpGetRealPath(mSession.userHomeDir, mSession.userCwd, mSession.ftpArg);

        if (rp.isEmpty()) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to change directory.");
            return;
        }

        File f = new File(rp);
        if (!f.exists() || !f.isDirectory()) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FILEFAIL, ' ', "Failed to change directory.");
            return;
        } else if (!checkFileAccess(rp)) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOPERM, ' ', "Permission denied.");
            return;
        }

        mSession.userCwd = "/" + rp.substring(mSession.userHomeDir.length()).replace('\\', '/');
        mSession.userCurrentDir = rp;

        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_CWDOK, ' ', "Directory successfully changed.");
    }

    private void cleanPasv() {
    }

    private void cleanPort() {
        mSession.userPortSocketAddr = "";
        mSession.userPortSocketPort = 0;
    }

    private boolean isPasvActivate() {
        return false;
    }

    private boolean isPortActivate() {
        return !mSession.userPortSocketAddr.isEmpty();
    }

    private boolean checkDataTransferOk() {
        if (!isPortActivate() && !isPortActivate()) {
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

    private void handlePort() {
        cleanPasv();
        cleanPort();
        String[] values = mSession.ftpArg.split(",");
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
    }

    private void handleDirCommon(boolean fullDetails, boolean statCmd) {
        if (!statCmd && !checkDataTransferOk()) {
            return;
        }

        String dirNameStr = mSession.userCurrentDir; //默认为当前路径

        String optionStr = "";
        String filterStr;
        if (!mSession.ftpArg.isEmpty() && mSession.ftpArg.startsWith("-")) {
            int spaceIndex = mSession.ftpArg.indexOf(' ');
            if (spaceIndex != -1) {
                optionStr = mSession.ftpArg.substring(1, spaceIndex);
                filterStr = mSession.ftpArg.substring(spaceIndex).trim();
            } else {
                optionStr = mSession.ftpArg;
                filterStr = "";
            }
        } else {
            filterStr = mSession.ftpArg;
        }

        boolean isDir = true;
        boolean useControl = false;

        if (!filterStr.isEmpty()) {
            String rp = FtpUtil.ftpGetRealPath(mSession.userHomeDir, mSession.userCwd, filterStr);
            if (!checkFileAccess(rp)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOPERM, ' ', "Permission denied.");
                return;
            }

            //检查是否是目录
            File tf = new File(rp);
            if (tf.isDirectory()) {
                isDir = true;
                dirNameStr = rp;
            } else {
                //获取文件所在目录
                isDir = false;
                dirNameStr = tf.getParent();
            }
        }

        //开始传输数据
        if (statCmd) {
            useControl = true;
            optionStr += 'a';
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_STATFILE_OK, '-', "Status follows:");
        } else {
            if (!ioOpenConnection("Here comes the directory listing.")) {
                cleanPasv();
                cleanPort();
                return;
            }
        }

        PrintWriter selectedWriter = useControl ? mOut : mSession.userDataTransferWriterAscii;

        //开始列举目录
        File fdir = new File(dirNameStr);
        File[] files = fdir.listFiles();
        if (files != null) {
            String modifyDate;
            for (File f : files) {
                selectedWriter.println(FtpUtil.ftpFileNameFormat(f));
            }
        }
        selectedWriter.flush();

        boolean transferSuccess = true;

        if (!statCmd) {
            ioCloseConnection();
        }

        if (statCmd) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_STATFILE_OK, ' ', "End of status");
        } else if (!transferSuccess) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADSENDNET, ' ', "Failure writing network stream.");
        } else {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_TRANSFEROK, ' ', "Directory send OK.");
        }

        checkAbort();

        if (!statCmd) {
            cleanPasv();
            cleanPort();
        }
    }

    private void handleFeatures() {
        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FEAT, '-', "Features:");
        FtpUtil.ftpWriteStringRaw(mOut, " SIZE");
        FtpUtil.ftpWriteStringRaw(mOut, " UTF8");
        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FEAT, ' ', "End");
    }

    private void handleOpts() {
        mSession.ftpArg = mSession.ftpArg.toUpperCase();
        if ("UTF8 ON".equals(mSession.ftpArg)) {
            mSession.isUTF8Required = true;
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_OPTSOK, ' ', "Always in UTF8 mode.");
        } else {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADOPTS, ' ', "Option not understood.");
        }
    }

    private boolean handlePass() {
        if (mSession.userAnon) {
            postLogin();
            return true;
        }

        return false;
    }

    private void parseUsernamePassword() {
        while (readCmdArg()) {
            if ("USER".equals(mSession.ftpCmd)) {
                if (mSession.ftpArg.isEmpty()) {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_LOGINERR, ' ', "Error get USER.");
                    break;
                }
                mSession.user = mSession.ftpArg;
                mSession.ftpArg = mSession.ftpArg.toUpperCase();
                mSession.userAnon = "FTP".equals(mSession.ftpArg) || "ANONYMOUS".equals(mSession.ftpArg);
                if (mSession.userAnon && mTunables.hostNoAnonPassword) {
                    mSession.ftpArg = "<no password>";
                    if (handlePass()) {
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
            } else if ("PASS".equals(mSession.ftpCmd)) {
                if (mSession.user.isEmpty()) {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NEEDUSER, ' ', "Login with USER first.");
                    continue;
                }
                if (handlePass()) {
                    break;
                } else {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_LOGINERR, ' ', "Login incorrect.");
                    if (!checkLoginFail()) {
                        break;
                    }
                }
            } else if ("QUIT".equals(mSession.ftpCmd)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_GOODBYE, ' ', "Goodbye.");
                break;
            } else if ("FEAT".equals(mSession.ftpCmd)) {
                handleFeatures();
            } else if ("OPTS".equals(mSession.ftpCmd)) {
                handleOpts();
            } else if (mSession.ftpCmd.isEmpty() && mSession.ftpArg.isEmpty()) {
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

            mSession = new FtpSession();
            String oAddr = mIncoming.getRemoteSocketAddress().toString();
            mSession.userRemoteAddr = oAddr.substring(1, oAddr.indexOf(':'));

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
                    if (c.equals(mSession.ftpCmd)) {
                        isCmdAllowed = true;
                        break;
                    }
                }
            } else if (mTunables.hostCmdsAllowed.length > 0) {
                for (String c : mTunables.hostCmdsAllowed) {
                    if (c.equals(mSession.ftpCmd)) {
                        isCmdAllowed = true;
                        break;
                    }
                }
            } else {
                isCmdAllowed = true;
            }
            if (mSession.userCmdsDenied.length > 0) {
                for (String c : mSession.userCmdsDenied) {
                    if (c.equals(mSession.ftpCmd)) {
                        isCmdAllowed = false;
                        break;
                    }
                }
            } else if (mTunables.hostCmdsDenied.length > 0) {
                for (String c : mTunables.hostCmdsDenied) {
                    if (c.equals(mSession.ftpCmd)) {
                        isCmdAllowed = false;
                        break;
                    }
                }
            }

            if (!isCmdAllowed) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOPERM, ' ', "Permission denied.");
            } else if ("QUIT".equals(mSession.ftpCmd)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_GOODBYE, ' ', "Goodbye.");
                break;
            } else if ("PWD".equals(mSession.ftpCmd) || "XPWD".equals(mSession.ftpCmd)) {
                //路径中的双引号加倍
                String cwd = mSession.userCwd.replace("\"", "\"\"");
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_PWDOK, ' ', "\"" + cwd + "\"");
            } else if ("CWD".equals(mSession.ftpCmd) || "XCWD".equals(mSession.ftpCmd)) {
                handleCwd();
            } else if ("CDUP".equals(mSession.ftpCmd) || "XCUP".equals(mSession.ftpCmd)) {
                mSession.ftpArg = "../";
                handleCwd();
            } else if ("PASV".equals(mSession.ftpCmd) || "P@SW".equals(mSession.ftpCmd)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADCMD, ' ', "PASV not support yet.");
            } else if ("RETR".equals(mSession.ftpCmd)) {

            } else if ("NOOP".equals(mSession.ftpCmd)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOOPOK, ' ', "NOOP ok.");
            } else if ("SYST".equals(mSession.ftpCmd)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_SYSTOK, ' ', "UNIX Type: L8");
                //FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_SYSTOK, ' ', "Windows_NT version 5.0");
            } else if ("LIST".equals(mSession.ftpCmd)) {
                handleDirCommon(true, false);
            } else if ("TYPE".equals(mSession.ftpCmd)) {
                if ("I".equals(mSession.ftpArg) || "L8".equals(mSession.ftpArg) || "L 8".equals(mSession.ftpArg)) {
                    mSession.isAscii = false;
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_TYPEOK, ' ', "Switching to Binary mode.");
                } else if ("A".equals(mSession.ftpArg) || "A N".equals(mSession.ftpArg)) {
                    mSession.isAscii = true;
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_TYPEOK, ' ', "Switching to ASCII mode.");
                } else {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADCMD, ' ', "Unrecognised TYPE command.");
                }
            } else if ("PORT".equals(mSession.ftpCmd)) {
                handlePort();
            } else if ("NLST".equals(mSession.ftpCmd)) {
                handleDirCommon(false, false);
            } else if ("ABOR".equals(mSession.ftpCmd) || "\377\364\377\362ABOR".equals(mSession.ftpCmd)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_ABOR_NOCONN, ' ', "No transfer to ABOR.");
            } else if ("FEAT".equals(mSession.ftpCmd)) {
                handleFeatures();
            } else if ("OPTS".equals(mSession.ftpCmd)) {
                handleOpts();
            } else if ("USER".equals(mSession.ftpCmd)) {
                if (mSession.userAnon) {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_LOGINERR, ' ', "Can't change from guest user.");
                } else if (mSession.user.equals(mSession.ftpArg)) {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_GIVEPWORD, ' ', "Any password will do.");
                } else {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_LOGINERR, ' ', "Can't change to another user.");
                }
            } else if ("STAT".equals(mSession.ftpCmd) && mSession.ftpArg.isEmpty()) {

            } else if ("STAT".equals(mSession.ftpCmd)) {
                handleDirCommon(true, true);
            } else if ("PASS".equals(mSession.ftpCmd)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_LOGINOK, ' ', "Already logged in.");
            } else if (mSession.ftpCmd.isEmpty() && mSession.ftpArg.isEmpty()) {

            } else {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADCMD, ' ', "Unknown command.");
            }
        }
        System.out.println("Oops!");
    }
}
