package org.foxteam.nosiyfox.fdf.Host;

import org.foxteam.nosiyfox.fdf.FtpCodes;
import org.foxteam.nosiyfox.fdf.FtpMain;
import org.foxteam.nosiyfox.fdf.FtpUtil;
import org.foxteam.nosiyfox.fdf.Tunables;
import sun.net.NetworkClient;

import java.io.*;
import java.net.Socket;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-21
 * Time: 下午10:04
 * To change this template use File | Settings | File Templates.
 */
public class HostServant extends Thread {
    protected static int mNumClients = 0;

    protected final Tunables mTunables;
    protected final Socket mIncoming;
    protected PrintWriter mOut;
    protected BufferedReader mIn;

    protected FtpSession mSession;

    protected HostServant(Tunables tunables, Socket socket) {
        mTunables = tunables;
        mIncoming = socket;
    }

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
        if (mTunables.hostMaxClients > 0 && mNumClients > mTunables.hostMaxClients) {
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
        } else if (!f.canRead()) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOPERM, ' ', "Permission denied.");
            return;
        }

        //验证是否为home的子目录
        if (!rp.startsWith(mSession.userHomeDir)) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NOPERM, ' ', "Permission denied.");
            return;
        }

        mSession.userCwd = "/" + rp.substring(mSession.userHomeDir.length());
        mSession.userCurrentDir = rp;

        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_CWDOK, ' ', "Directory successfully changed.");
    }

    private void handlePort() {
        mSession.userSocketAddr = "";
        mSession.userSocketPort = 0;
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
        if(!sockAddr.equals(mSession.userRemoteAddr) || sockPort < FtpMain.IPPORT_RESERVED){
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADCMD, ' ', "Illegal PORT command.");
            return;
        }

        mSession.userSocketAddr = sockAddr;
        mSession.userSocketPort = sockPort;

        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_PORTOK, ' ', "PORT command successful. Consider using PASV.");
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
            mNumClients++;
            //开始监听
            try {
                mOut = new PrintWriter(mIncoming.getOutputStream(), true);
                mIn = new BufferedReader(new InputStreamReader(mIncoming.getInputStream()));
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
        mNumClients--;
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
            } else if ("LIST".equals(mSession.ftpCmd)) {

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
