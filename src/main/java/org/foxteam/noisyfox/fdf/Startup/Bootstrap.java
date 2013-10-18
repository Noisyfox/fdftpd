package org.foxteam.noisyfox.fdf.Startup;

import org.foxteam.noisyfox.fdf.FtpMain;
import org.foxteam.noisyfox.fdf.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-10-13
 * Time: 下午12:32
 * 服务启动器
 */
public class Bootstrap {
    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

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
            log.error("Bootstrap: command \"{}\" does not exist.", command);
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
