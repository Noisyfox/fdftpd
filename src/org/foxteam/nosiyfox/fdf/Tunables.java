package org.foxteam.nosiyfox.fdf;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-21
 * Time: 下午9:46
 * To change this template use File | Settings | File Templates.
 */
public class Tunables {
    public boolean isHost = true;

    public int hostMaxClients = 2000;
    public int hostListenPort = 21;
    public int hostDataPort = 20;
    public int hostMaxLoginFails = 3;

    public boolean hostNoAnonPassword = true;

    public String[] hostCmdsAllowed = {}; //全局白名单
    public String[] hostCmdsDenied = {}; //全局黑名单

    public void loadFromFile(String filePath){

    }

}
