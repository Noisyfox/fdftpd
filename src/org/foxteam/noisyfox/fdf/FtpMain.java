package org.foxteam.noisyfox.fdf;

import org.foxteam.noisyfox.fdf.Host.Host;
import org.foxteam.noisyfox.fdf.Node.Node;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.AccessControlException;
import java.util.Random;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-21
 * Time: 下午9:41
 * To change this template use File | Settings | File Templates.
 */
public class FtpMain extends Thread implements Server {
    public static final String FDF_VER = "0.1";
    public static final int IPPORT_RESERVED = 1024;
    public static final String CONFIG_DEFAULT_PATH = "fdftpd.conf";

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

        tunables.loadFromFile(CONFIG_DEFAULT_PATH);
        for (String arg : args) {
            if (arg.startsWith("-")) {//设置
                tunables.parseSetting(arg.substring(1), false);
            } else {//配置文件
                tunables.loadFromFile(arg);
            }
        }

        return tunables;
    }

    Server fdServer = null;
    String shutdown = "SHUTDOWN";
    Random random = null;

    @Override
    public void run() {
        if (fdServer == null) {

        } else {
            fdServer.startServer(null);
        }
        System.out.println("Server stopped!");
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
        System.out.println("Server exit!");
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
            System.out.println("The server was successfully shut down.");
        } catch (IOException e) {
            System.out.println("Error. The server has not been started.");
        }
    }

    public void await(int port) {
        // Set up a server socket to wait on
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"));
        } catch (IOException e) {
            System.err.println("FtpMain.await: create[" + port + "]: " + e);
            e.printStackTrace();
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
                System.err.println("FtpMain.accept security exception: " + ace.getMessage());
                continue;
            } catch (IOException e) {
                System.err.println("FtpMain.await: accept: " + e);
                e.printStackTrace();
                System.exit(1);
            }

            // Read a set of characters from the socket
            StringBuffer command = new StringBuffer();
            int expected = 1024; // Cut off to avoid DoS attack
            while (expected < shutdown.length()) {
                if (random == null) random = new Random(System.currentTimeMillis());
                expected += (random.nextInt() % 1024);
            }
            while (expected > 0) {
                int ch = -1;
                try {
                    ch = stream.read();
                } catch (IOException e) {
                    System.err.println("FtpMain.await: read: " + e);
                    e.printStackTrace();
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
            } else System.err.println("FtpMain.await: Invalid command '" + command.toString() + "' received");
        }
        // Close the server socket and return
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
    }
}
