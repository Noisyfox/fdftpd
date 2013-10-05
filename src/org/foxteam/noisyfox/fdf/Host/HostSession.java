package org.foxteam.noisyfox.fdf.Host;

import org.foxteam.noisyfox.fdf.*;
import org.foxteam.noisyfox.fdf.FilePermission;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-22
 * Time: 上午12:27
 * To change this template use File | Settings | File Templates.
 */
public class HostSession extends RateRestriction {
    protected String ftpCmd = "";
    protected String ftpArg = "";

    protected String user = "";
    protected boolean userAnon = false;
    protected int loginFails = 0;

    protected boolean isAscii = false;
    protected boolean isUTF8Required = false;

    protected String[] userCmdsAllowed = {}; //用户白名单
    protected String[] userCmdsDenied = {}; //用户黑名单
    protected FilePermission permission = null;

    protected Path userHomeDir = Path.valueOf("C:\\");
    protected Path userCurrentDir = userHomeDir;

    protected String userRemoteAddr = "";
    protected String userPortSocketAddr = "";
    protected int userPortSocketPort = 0;

    protected ServerSocket userPasvSocketServer = null;

    //数据传输流
    protected boolean userDataTransferAborReceived = false;
    protected Socket userDataTransferSocket = null;
    protected BufferedReader userDataTransferReaderAscii = null;
    protected BufferedInputStream userDataTransferReaderBinary = null;
    protected PrintWriter userDataTransferWriterAscii = null;
    protected BufferedOutputStream userDataTransferWriterBinary = null;

    //文件传输状态
    protected long userFileRestartOffset = 0;

    protected File userRnfrFile = null;

}
