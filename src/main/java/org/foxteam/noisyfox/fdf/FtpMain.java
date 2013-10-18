package org.foxteam.noisyfox.fdf;

import org.foxteam.noisyfox.fdf.Host.Host;
import org.foxteam.noisyfox.fdf.Node.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.AccessControlException;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-21
 * Time: 下午9:41
 * 服务器主入口
 */
public class FtpMain extends Thread implements Server {
    private static final Logger log = LoggerFactory.getLogger(FtpMain.class);
    public static final String FDF_VER = "0.1";
    public static final int IPPORT_RESERVED = 1024;
    public static final String CONFIG_DEFAULT_PATH = "./config/fdftpd.conf";

    public static void main(String args[]) {
        Tunables tunables = loadTunables(args);

        if (tunables.isHost) {
            Host h = new Host(tunables);
            h.hostStart();
        } else {
            Node n = new Node(tunables);
            n.nodeStart();
        }

    }

    private static Tunables loadTunables(String args[]) {
        Tunables tunables = new Tunables();
        String serverHome = System.getProperty("fdftpd.home");
        if (serverHome != null) {
            tunables.serverHome = Path.valueOf(serverHome);
        }

        tunables.loadFromFile(FtpUtil.ftpGetRealPath(tunables.serverHome, tunables.serverHome, Path.valueOf(CONFIG_DEFAULT_PATH)).getAbsolutePath());
        for (String arg : args) {
            if (arg.startsWith("-")) {//设置
                tunables.parseSetting(arg.substring(1), false);
            } else {//配置文件
                Path path = FtpUtil.ftpGetRealPath(tunables.serverHome, tunables.serverHome, Path.valueOf(arg));
                tunables.loadFromFile(path.getAbsolutePath());
            }
        }

        return tunables;
    }

    Server fdServer = null;
    String shutdown = "SHUTDOWN";

    @Override
    public void run() {
        if (fdServer == null) {
            IllegalStateException exception = new IllegalStateException("Server not created!");
            log.error(exception.getMessage(), exception);
            throw exception;
        } else {
            fdServer.startServer(null);
        }
        log.info("Server stopped!");
    }

    @Override
    public void startServer(String[] args) {
        Tunables tunables = loadTunables(args);

        if (tunables.isHost) {
            fdServer = new Host(tunables);
        } else {
            fdServer = new Node(tunables);
        }
        start();
        await(tunables.serverControlPort);
        fdServer.stopServer(null);
        log.info("Server exit!");
    }

    @Override
    public void stopServer(String[] args) {
        Tunables tunables = loadTunables(args);
        try {
            Socket socket = new Socket("127.0.0.1", tunables.serverControlPort);
            OutputStream stream = socket.getOutputStream();
            for (int i = 0; i < shutdown.length(); i++) stream.write(shutdown.charAt(i));
            stream.flush();
            stream.close();
            socket.close();
            log.info("The server was successfully shut down.");
        } catch (IOException e) {
            log.info("Error. The server has not been started.");
        }
    }

    public void await(int port) {
        // Set up a server socket to wait on
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            log.error("FtpMain.await: create[{}]: {}", port, e);
            System.exit(1);
        }

        // Loop waiting for a connection and a valid command
        while (true) {
            // Wait for the next connection
            Socket socket = null;
            InputStream stream = null;
            try {
                socket = serverSocket.accept();
                socket.setSoTimeout(10 * 1000); // Ten seconds
                stream = socket.getInputStream();
            } catch (AccessControlException ace) {
                log.error("FtpMain.accept security exception", ace);
                continue;
            } catch (IOException e) {
                log.error("FtpMain.await: accept", e);
                System.exit(1);
            }

            // Read a set of characters from the socket
            StringBuilder command = new StringBuilder();
            int expected = 1024; // Cut off to avoid DoS attack
            while (expected < shutdown.length()) {
                expected += (FtpUtil.generator.nextInt() % 1024);
            }
            while (expected > 0) {
                int ch;
                try {
                    ch = stream.read();
                } catch (IOException e) {
                    log.error("FtpMain.await: read", e);
                    ch = -1;
                }
                if (ch < 32) // Control character or EOF terminates loop
                    break;
                command.append((char) ch);
                expected--;
            }
            // Close the socket now that we are done with it
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            // Match against our command string
            boolean match = command.toString().equals(shutdown);
            if (match) {
                break;
            } else log.error("FtpMain.await: Invalid command '{}' received", command.toString());
        }
        // Close the server socket and return
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
    }
}
