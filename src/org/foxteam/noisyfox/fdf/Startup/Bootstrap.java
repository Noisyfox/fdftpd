package org.foxteam.noisyfox.fdf.Startup;

import org.foxteam.noisyfox.fdf.FtpMain;
import org.foxteam.noisyfox.fdf.Server;

import java.util.Arrays;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-10-13
 * Time: 下午12:32
 * To change this template use File | Settings | File Templates.
 */
public class Bootstrap {
    private static Bootstrap daemon = null;

    public static void main(String args[]) {
        if (daemon == null) {
            daemon = new Bootstrap();
        }

        //解析启动命令
        String command = "start";
        String newArgs[] = {};
        if (args.length > 0) {
            command = args[0];
            newArgs = Arrays.copyOfRange(args, 1, args.length);
        }

        if ("start".equals(command)) {
            daemon.startServer(newArgs);
        } else if ("stop".equals(command)) {
            daemon.stopServer(newArgs);
        } else {
            System.out.println("Bootstrap: command \"" + command + "\" does not exist.");
        }
    }

    Server fdftpdServer = null;

    void startServer(String args[]) {
        fdftpdServer = new FtpMain();
        fdftpdServer.startServer(args);
    }

    void stopServer(String args[]) {
        fdftpdServer = new FtpMain();
        fdftpdServer.stopServer(args);
    }
}
