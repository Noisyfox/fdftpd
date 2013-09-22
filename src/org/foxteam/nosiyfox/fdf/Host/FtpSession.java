package org.foxteam.nosiyfox.fdf.Host;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-22
 * Time: 上午12:27
 * To change this template use File | Settings | File Templates.
 */
public class FtpSession {
    protected String ftpCmd = "";
    protected String ftpArg = "";

    protected String user = "";
    protected boolean userAnon = false;
    protected int loginFails = 0;

    protected boolean isAscii = false;

    protected String[] userCmdsAllowed = {}; //用户白名单
    protected String[] userCmdsDenied = {}; //用户黑名单

    protected String userCwd = "/";
    protected String userHomeDir = "C:\\";
    protected String userCurrentDir = userHomeDir;

    protected String userRemoteAddr = "";
    protected String userSocketAddr = "";
    protected int userSocketPort = 0;
}
