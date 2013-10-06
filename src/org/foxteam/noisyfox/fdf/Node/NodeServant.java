package org.foxteam.noisyfox.fdf.Node;

import org.foxteam.noisyfox.fdf.FtpCodes;
import org.foxteam.noisyfox.fdf.FtpUtil;
import org.foxteam.noisyfox.fdf.RequestCmdArg;

import java.io.*;
import java.net.Socket;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-25
 * Time: 下午9:07
 * To change this template use File | Settings | File Templates.
 */
public class NodeServant extends Thread {

    private NodeSession mSession;

    private final Socket mSocket;
    private final PrintWriter mWriter;
    private final BufferedReader mReader;

    private RequestCmdArg mHostCmdArg = new RequestCmdArg();

    public NodeServant(Socket socket) throws IOException {
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
        System.out.println("Node servant started!");
        mSession = new NodeSession();
        servantLoop();
    }

    private boolean readCmdArg() {
        return FtpUtil.readCmdArg(mSocket, mReader, mHostCmdArg, 0, "Host");
    }

    private void servantLoop() {
        while (readCmdArg()) {
            if ("NOOP".equals(mHostCmdArg.mCmd)) {
                FtpUtil.ftpWriteStringCommon(mWriter, FtpCodes.FTP_NOOPOK, ' ', "NOOP ok.");
            } else if ("UNAME".equals(mHostCmdArg.mCmd)) {
                mSession.user = mHostCmdArg.mArg;
                FtpUtil.ftpWriteStringCommon(mWriter, FtpCodes.HOST_INFOOK, ' ', "Remote user updated:" + mSession.user);
            } else if ("RADDR".equals(mHostCmdArg.mCmd)) {
                mSession.userRemoteAddr = mHostCmdArg.mArg;
                FtpUtil.ftpWriteStringCommon(mWriter, FtpCodes.HOST_INFOOK, ' ', "Remote address updated:" + mSession.userRemoteAddr);
            } else {
                FtpUtil.ftpWriteStringCommon(mWriter, FtpCodes.FTP_BADCMD, ' ', "Unknown command.");
            }
        }
    }

}
