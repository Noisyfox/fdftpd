package org.foxteam.noisyfox.fdf;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-21
 * Time: 下午10:11
 * To change this template use File | Settings | File Templates.
 */
public class FtpCodes {
    public static final int ANYCODE = -1;

    public static final int FTP_DATACONN = 150;

    public static final int FTP_NOOPOK = 200;
    public static final int FTP_TYPEOK = 200;
    public static final int FTP_PORTOK = 200;
    public static final int FTP_EPRTOK = 200;
    public static final int FTP_UMASKOK = 200;
    public static final int FTP_CHMODOK = 200;
    public static final int FTP_EPSVALLOK = 200;
    public static final int FTP_STRUOK = 200;
    public static final int FTP_MODEOK = 200;
    public static final int FTP_PBSZOK = 200;
    public static final int FTP_PROTOK = 200;
    public static final int FTP_OPTSOK = 200;
    public static final int FTP_ALLOOK = 202;
    public static final int FTP_FEAT = 211;
    public static final int FTP_STATOK = 211;
    public static final int FTP_SIZEOK = 213;
    public static final int FTP_MDTMOK = 213;
    public static final int FTP_STATFILE_OK = 213;
    public static final int FTP_SITEHELP = 214;
    public static final int FTP_HELP = 214;
    public static final int FTP_SYSTOK = 215;
    public static final int FTP_GREET = 220;
    public static final int FTP_GOODBYE = 221;
    public static final int FTP_ABOR_NOCONN = 225;
    public static final int FTP_TRANSFEROK = 226;
    public static final int FTP_ABOROK = 226;
    public static final int FTP_PASVOK = 227;
    public static final int FTP_EPSVOK = 229;
    public static final int FTP_LOGINOK = 230;
    public static final int FTP_AUTHOK = 234;
    public static final int FTP_CWDOK = 250;
    public static final int FTP_RMDIROK = 250;
    public static final int FTP_DELEOK = 250;
    public static final int FTP_RENAMEOK = 250;
    public static final int FTP_PWDOK = 257;
    public static final int FTP_MKDIROK = 257;

    public static final int FTP_GIVEPWORD = 331;
    public static final int FTP_RESTOK = 350;
    public static final int FTP_RNFROK = 350;

    public static final int FTP_IDLE_TIMEOUT = 421;
    public static final int FTP_DATA_TIMEOUT = 421;
    public static final int FTP_TOO_MANY_USERS = 421;
    public static final int FTP_IP_LIMIT = 421;
    public static final int FTP_IP_DENY = 421;
    public static final int FTP_TLS_FAIL = 421;
    public static final int FTP_BADSENDCONN = 425;
    public static final int FTP_BADSENDNET = 426;
    public static final int FTP_BADSENDFILE = 451;

    public static final int FTP_BADCMD = 500;
    public static final int FTP_BADOPTS = 501;
    public static final int FTP_COMMANDNOTIMPL = 502;
    public static final int FTP_NEEDUSER = 503;
    public static final int FTP_NEEDRNFR = 503;
    public static final int FTP_BADPBSZ = 503;
    public static final int FTP_BADPROT = 503;
    public static final int FTP_BADSTRU = 504;
    public static final int FTP_BADMODE = 504;
    public static final int FTP_BADAUTH = 504;
    public static final int FTP_NOSUCHPROT = 504;
    public static final int FTP_NEEDENCRYPT = 522;
    public static final int FTP_EPSVBAD = 522;
    public static final int FTP_DATATLSBAD = 522;
    public static final int FTP_LOGINERR = 530;
    public static final int FTP_NOHANDLEPROT = 536;
    public static final int FTP_FILEFAIL = 550;
    public static final int FTP_NOPERM = 550;
    public static final int FTP_UPLOADFAIL = 553;

    //Host Node通讯代码
    public static final int HOST_CHALLENGEOK = 900;
    public static final int HOST_BADRESPONDE = 901;
    public static final int HOST_RESPONDEOK = 902;
    public static final int HOST_DMAP = 903;
    public static final int HOST_BADSENDCONN = 904;
    public static final int HOST_INFOOK = 905;
    public static final int HOST_CONFOK = 906;

    public static final int NODE_OPSMSG = 907;
    public static final int NODE_OPSOK = 908;
    public static final int NODE_BRIDGEOK = 909;
    public static final int NODE_BADBRIDGE = 910;
    public static final int NODE_CHARSETOK = 911;

}
