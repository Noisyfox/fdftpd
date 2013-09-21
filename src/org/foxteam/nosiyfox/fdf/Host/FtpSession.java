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
}
