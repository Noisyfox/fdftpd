package org.foxteam.noisyfox.fdf.Host;

import java.io.*;
import java.net.Socket;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-27
 * Time: 下午12:53
 * To change this template use File | Settings | File Templates.
 */
public class HostNodeSession extends Thread {
    private final Socket mSocket;
    private final PrintWriter mWriter;
    private final BufferedReader mReader;

    public HostNodeSession(Socket socket) throws IOException {
        mSocket = socket;
        try {
            mReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            mWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            throw new IOException(e);
        }
    }

    @Override
    public void run() {
        System.out.println("Node session created!");
        while (true) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }
}
