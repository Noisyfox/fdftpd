package org.foxteam.noisyfox.fdf.Node;

import org.foxteam.noisyfox.fdf.RateRestriction;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-25
 * Time: 下午7:57
 * To change this template use File | Settings | File Templates.
 */
public class NodeSession extends RateRestriction {
    protected String hostCmd = "";
    protected String hostArg = "";

    protected String user = "";
    protected boolean userAnon = false;

    protected boolean isAscii = false;
    protected boolean isUTF8Required = false;

    protected String[] userCmdsAllowed = {}; //用户白名单
    protected String[] userCmdsDenied = {}; //用户黑名单

    protected String userCwd = "/";
    protected String userHomeDir = "C:\\";
    protected String userCurrentDir = userHomeDir;

    protected String userRemoteAddr = "";
    protected String userPortSocketAddr = "";
    protected int userPortSocketPort = 0;
}
