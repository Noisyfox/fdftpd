package org.foxteam.noisyfox.fdf;

import org.foxteam.noisyfox.fdf.Host.Host;
import org.foxteam.noisyfox.fdf.Node.Node;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-21
 * Time: 下午9:41
 * To change this template use File | Settings | File Templates.
 */
public class FtpMain {
    public static final String FDF_VER = "0.1";
    public static final int IPPORT_RESERVED = 1024;
    public static final String CONFIG_DEFAULT_PATH = "fdftpd.conf";

    public static void main(String args[]) {
        Tunables tunables = new Tunables();

        tunables.loadFromFile(CONFIG_DEFAULT_PATH);
        for (String arg : args) {
            if (arg.startsWith("-")) {//设置
                tunables.parseSetting(arg.substring(1), false);
            } else {//配置文件
                tunables.loadFromFile(arg);
            }
        }

        if (tunables.isHost) {
            Host h = new Host(tunables);
            h.hostStart();
        } else {
            Node n = new Node(tunables);
            n.nodeStart();
        }

    }
}
