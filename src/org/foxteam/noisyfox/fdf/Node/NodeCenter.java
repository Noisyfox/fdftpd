package org.foxteam.noisyfox.fdf.Node;

import org.foxteam.noisyfox.fdf.FtpCertification;
import org.foxteam.noisyfox.fdf.FtpCodes;
import org.foxteam.noisyfox.fdf.FtpUtil;
import org.foxteam.noisyfox.fdf.Tunables;

import java.io.*;
import java.net.Socket;
import java.util.Random;
import java.util.Vector;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-25
 * Time: 下午8:28
 * To change this template use File | Settings | File Templates.
 */
public class NodeCenter {
    private final static Random generator = new Random();//随机数

    protected final FtpCertification mCert;
    protected final Tunables mTunables;
    protected final Socket mIncoming;
    protected final Vector<NodeDirectoryMapper> mDirectoryMappers;
    protected PrintWriter mOut;
    protected BufferedReader mIn;
    private String mHostCmd = "";
    private String mHostArg = "";

    public NodeCenter(Vector<NodeDirectoryMapper> directoryMappers, FtpCertification cert, Tunables tunables, Socket socket){
        mDirectoryMappers = directoryMappers;
        mCert = cert;
        mTunables = tunables;
        mIncoming = socket;
    }


    private boolean readCmdArg() {
        mHostCmd = "";
        mHostArg = "";
        try {
            String line = mIn.readLine();
            if (line != null) {
                System.out.println("Host command:" + line);
                int i = line.indexOf(' ');
                if (i != -1) {
                    mHostCmd = line.substring(0, i).trim().toUpperCase();
                    mHostArg = line.substring(i).trim();
                } else {
                    mHostCmd = line;
                }
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return false;
    }

    public void startNode(){
        //开始监听
        try {
            mOut = new PrintWriter(new OutputStreamWriter(mIncoming.getOutputStream(), mTunables.hostRemoteCharset), true);
            mIn = new BufferedReader(new InputStreamReader(mIncoming.getInputStream(), mTunables.hostRemoteCharset));
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            return;
        }

        //验证服务端身份
        if(verifyHost()){
            serviceLoop();
        }

    }

    public void tryDoClean(){
        try{

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public boolean verifyHost(){
        String challenge = FtpCertification.generateChallenge();
        while(readCmdArg()){
            if("CHAG".equals(mHostCmd)){
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.HOST_CHALLENGEOK, ' ', challenge);
            } else if ("REPO".equals(mHostCmd)){
                if(mCert.verify(challenge, mHostArg)){
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

    public void serviceLoop(){
        while (readCmdArg()){
            if("DMAP".equals(mHostCmd)){
                handleDmap();
            } else {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.FTP_BADCMD, ' ', "Unknown command.");
            }
        }
    }

    private void handleDmap(){
        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.HOST_DMAP, '-', "DirMaps:");
        for(NodeDirectoryMapper ndm : mDirectoryMappers){
             FtpUtil.ftpWriteStringRaw(mOut, " " + ndm.dirTo.getAbsolutePath());
        }
        FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.HOST_DMAP, ' ', "End");
    }

}
