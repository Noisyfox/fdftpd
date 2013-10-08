package org.foxteam.noisyfox.fdf.Node;

import org.foxteam.noisyfox.fdf.FtpCodes;
import org.foxteam.noisyfox.fdf.FtpUtil;
import org.foxteam.noisyfox.fdf.Path;
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
    private final NodeDirectoryMapper mDirectoryMapper;

    private RequestCmdArg mHostCmdArg = new RequestCmdArg();

    public NodeServant(NodeDirectoryMapper dirMapper, Socket socket) throws IOException {
        mDirectoryMapper = dirMapper;
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
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_NOOPOK, ' ', "NOOP ok.");
            } else if ("UNAME".equals(mHostCmdArg.mCmd)) {
                mSession.user = mHostCmdArg.mArg;
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.HOST_INFOOK, ' ', "Remote user updated:", mSession.user);
            } else if ("RADDR".equals(mHostCmdArg.mCmd)) {
                mSession.userRemoteAddr = mHostCmdArg.mArg;
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.HOST_INFOOK, ' ', "Remote address updated:", mSession.userRemoteAddr);
            } else if ("CWD".equals(mHostCmdArg.mCmd)) {
                handleCwd();
            } else if ("SIZE".equals(mHostCmdArg.mCmd)) {
                handleSize();
            } else {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_BADCMD, ' ', "Unknown command.");
            }
        }
    }

    private void handleCwd() {
        Path rp = mDirectoryMapper.map(Path.valueOf(mHostCmdArg.mArg));
        if (rp == null) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Failed to change directory.");
        } else {
            File f = rp.getFile();
            if (!f.exists() || !f.isDirectory()) {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Failed to change directory.");
            } else {
                mSession.userCurrentDir = rp;
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_CWDOK, ' ', "Directory successfully changed.");
            }
        }
    }

    private void handleSize() {
        Path rp = mDirectoryMapper.map(Path.valueOf(mHostCmdArg.mArg));
        if (rp == null) {
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Could not get file size.");
        } else {
            File f = rp.getFile();
            if (!f.exists() || f.isDirectory()) {
                FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_FILEFAIL, ' ', "Could not get file size.");
                return;
            }
            FtpUtil.ftpWriteNodeString(mWriter, FtpCodes.NODE_OPSOK, FtpCodes.FTP_SIZEOK, ' ', String.valueOf(f.length()));
        }
    }

}
