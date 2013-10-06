package org.foxteam.noisyfox.fdf.Host;

import org.foxteam.noisyfox.fdf.FtpCodes;
import org.foxteam.noisyfox.fdf.FtpUtil;
import org.foxteam.noisyfox.fdf.RequestStatus;

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
    private final Object mWaitObj = new Object();
    private final Socket mSocket;
    private final PrintWriter mWriter;
    private final BufferedReader mReader;

    RequestStatus mNodeStatus = new RequestStatus();

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

    private boolean readStatus(boolean longWait) {
        return FtpUtil.readStatus(mSocket, mReader, mNodeStatus, longWait ? 0 : 10000, "Node");
    }

    /**
     * 连接准备，主机和node进行必要的信息交换，并启动session至可使用状态
     *
     * @param userSession
     */
    public boolean prepareConnection(HostSession userSession) {
        //告知登录用户名
        FtpUtil.ftpWriteStringRaw(mWriter, "UNAME " + userSession.user);
        if (!readStatus(false) || mNodeStatus.mStatusCode != FtpCodes.HOST_INFOOK) {
            System.out.println("Error exchange information.");
            return false;
        }
        //告知客户端地址
        FtpUtil.ftpWriteStringRaw(mWriter, "RADDR " + userSession.userRemoteAddr);
        if (!readStatus(false) || mNodeStatus.mStatusCode != FtpCodes.HOST_INFOOK) {
            System.out.println("Error exchange information.");
            return false;
        }

        this.start();

        return true;
    }

    @Override
    public void run() {
        System.out.println("Node session created!");
        synchronized (mWaitObj) {//这个线程用来发送心跳包
            while (true) {
                FtpUtil.ftpWriteStringRaw(mWriter, "NOOP");//发送心跳
                if (!readStatus(false) || mNodeStatus.mStatusCode != FtpCodes.FTP_NOOPOK) { //挂了
                    break;
                }
                try {
                    mWaitObj.wait(10000);//等待时释放锁，使该session可以处理外界请求
                    //结束等待时会尝试获取锁，此时如果外界请求没有结束，则会阻塞，否则开始发送心跳
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

}
