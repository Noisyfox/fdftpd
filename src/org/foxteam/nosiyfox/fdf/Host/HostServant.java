package org.foxteam.nosiyfox.fdf.Host;

import org.foxteam.nosiyfox.fdf.FtpCodes;
import org.foxteam.nosiyfox.fdf.FtpMain;
import org.foxteam.nosiyfox.fdf.FtpUtil;
import org.foxteam.nosiyfox.fdf.Tunables;

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
                }else{
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

    private boolean checkLoginFail(){
        mSession.loginFails++;
        return mSession.loginFails <= mTunables.hostMaxLoginFails;
    }

    private void features() {
        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FEAT, '-', "Features:");
        FtpUtil.ftpWriteStringRaw(mOut, " SIZE");
        FtpUtil.ftpWriteStringRaw(mOut, " UTF8");
        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_FEAT, ' ', "End:");
    }

    private void handleOpts() {
        mSession.ftpArg = mSession.ftpArg.toUpperCase();
        if ("UTF8 ON".equals(mSession.ftpArg)) {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_OPTSOK, ' ', "Always in UTF8 mode.");
        } else {
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADOPTS, ' ', "Option not understood.");
        }
    }

    private boolean handlePass(){
        if(mSession.userAnon){
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
                    if(handlePass()){
                        break;
                    }else{
                        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_LOGINERR, ' ', "Login incorrect.");
                        if(!checkLoginFail()){
                            break;
                        }
                    }
                }else{
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_GIVEPWORD, ' ', "Please specify the password.");
                }
            } else if ("PASS".equals(mSession.ftpCmd)) {
                if (mSession.user.isEmpty()) {
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_NEEDUSER, ' ', "Login with USER first.");
                    continue;
                }
                if(handlePass()){
                    break;
                }else{
                    FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_LOGINERR, ' ', "Login incorrect.");
                    if(!checkLoginFail()){
                        break;
                    }
                }
            } else if ("QUIT".equals(mSession.ftpCmd)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_GOODBYE, ' ', "Goodbye.");
                break;
            } else if ("FEAT".equals(mSession.ftpCmd)) {
                features();
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

            parseUsernamePassword();

            break;
        }
        doClean();
        mNumClients--;
        System.out.println("Session exit!");
    }

    private void postLogin(){
        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_LOGINOK, ' ', "Login successful.");
        while (readCmdArg()) {

        }
        System.out.println("Oops!");
    }
}
