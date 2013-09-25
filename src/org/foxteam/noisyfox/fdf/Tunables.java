package org.foxteam.noisyfox.fdf;

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


    public String[] hostCmdsAllowed = {}; //全局白名单
    public String[] hostCmdsDenied = {}; //全局黑名单

    public String hostRemoteCharset = "GBK";//控制流使用的默认编码
    public String hostDefaultTransferCharset = "GBK";//Ascii传输流默认使用GBK编码

    public long hostTransferRateMax = 1024 * 1024 * 10;//Long.MAX_VALUE;

    public boolean hostPasvEnabled = true;
    public boolean hostPortEnabled = true;
    public boolean hostWriteEnabled = false;
    public boolean hostDownloadEnabled = true;
    public boolean hostDirListEnabled = true;
    public boolean hostChmodEnabled = true;
    public boolean hostAsciiUploadEnabled = false;
    public boolean hostAsciiDownloadEnabled = false;

    public boolean hostAnonNoPassword = false;
    public boolean hostAnonUploadEnabled = false;
    public boolean hostAnonMkdirWriteEnabled = false;
    public boolean hostAnonOtherWriteEnabled = false;

    public void loadFromFile(String filePath) {

    }

}
