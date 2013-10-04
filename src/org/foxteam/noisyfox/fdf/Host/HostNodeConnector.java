package org.foxteam.noisyfox.fdf.Host;

import org.foxteam.noisyfox.fdf.FtpCodes;
import org.foxteam.noisyfox.fdf.FtpUtil;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-28
 * Time: 下午2:17
 * To change this template use File | Settings | File Templates.
 */
public class HostNodeConnector extends Thread {
    private final static Random generator = new Random();//随机数

    private final Object mWaitObject = new Object();
    private final HostNodeDefinition mHostNodeDefinition;
    private final HostDirectoryMapper mHostDirectoryMapper;
    private final Queue<SessionRequest> mSessionRequestQueue = new LinkedList<SessionRequest>();
    protected Socket mConnecting;
    protected PrintWriter mOut;
    protected BufferedReader mIn;
    private int mStatusCode;
    private String mStatusMsg;

    public HostNodeConnector(HostNodeDefinition nodeDef, HostDirectoryMapper dirMapper) {
        mHostNodeDefinition = nodeDef;
        mHostDirectoryMapper = dirMapper;
    }

    private boolean readStatus() {
        mStatusCode = 0;
        mStatusMsg = "";
        try {
            mConnecting.setSoTimeout(20000);
            String line = mIn.readLine();
            mConnecting.setSoTimeout(0);
            if (line != null) {
                System.out.println("Node status:" + line);
                int i = line.indexOf(' ');
                int i2 = line.indexOf('-');
                if (i == -1) i = i2;
                else if (i2 != -1) i = Math.min(i, i2);

                if (i != -1) {
                    String code = line.substring(0, i).trim();
                    if (!code.isEmpty()) {
                        try {
                            mStatusCode = Integer.parseInt(code);
                        } catch (NumberFormatException ignored) {
                        }
                    }

                    if (i + 1 >= line.length() - 1) {
                        mStatusMsg = "";
                    } else {
                        mStatusMsg = line.substring(i + 1).trim();
                    }
                }
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void run() {
        while (true) {
            mConnecting = null;
            mOut = null;
            mIn = null;
            doConnect();
            //clean
            if (mIn != null) {
                try {
                    mIn.close();
                } catch (IOException ignored) {
                }
            }
            if (mOut != null) {
                mOut.close();
            }
            if (mConnecting != null) {
                try {
                    mConnecting.close();
                } catch (IOException ignored) {
                }
            }
            System.out.println("Connection lost or failed, reconnect in 5 seconds.");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void doConnect() {
        Socket tempSocket = null;
        BufferedReader tempReader = null;
        PrintWriter tempWriter;
        try {
            tempSocket = new Socket(mHostNodeDefinition.adderss, mHostNodeDefinition.port);
            tempReader = new BufferedReader(new InputStreamReader(tempSocket.getInputStream()));
            tempWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(tempSocket.getOutputStream())));
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println(String.format("Unable to connect to node%d-%s:%d", mHostNodeDefinition.number,
                    mHostNodeDefinition.adderss, mHostNodeDefinition.port));
            if (tempReader != null) {
                try {
                    tempReader.close();
                } catch (IOException ignored) {
                }
            }
            if (tempSocket != null) {
                try {
                    tempSocket.close();
                } catch (IOException ignored) {
                }
            }
            return;
        }
        mConnecting = tempSocket;
        mIn = tempReader;
        mOut = tempWriter;

        //verify
        if (!doVerify()) return;
        System.out.println("Node verify ok.");

        //request for dir map
        updateDirectoryMapper();

        //主逻辑
        while (true) {
            synchronized (mWaitObject) {
                FtpUtil.ftpWriteStringRaw(mOut, "NOOP");//发送心跳
                if (!readStatus() || mStatusCode != FtpCodes.FTP_NOOPOK) { //挂了
                    break;
                }
                while (!mSessionRequestQueue.isEmpty()) {//处理node连接请求
                    SessionRequest sessionRequest = mSessionRequestQueue.poll();
                    doPort(sessionRequest);
                    synchronized (sessionRequest.mWaitObj) {
                        sessionRequest.mWaitObj.notify();
                    }
                }
                try {
                    mWaitObject.wait(1000 * 10);//等待10秒后重新开始检测
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private void doPort(SessionRequest sessionRequest) {
        //打开连接端口
        int bindRetry = 10;
        int minPort = 1024;
        int maxPort = 65535;
        int selectedPort;
        ServerSocket ss;
        while (true) {
            bindRetry--;
            if (bindRetry <= 0) {
                return;
            }
            selectedPort = minPort + generator.nextInt(maxPort - minPort) + 1;//随机端口
            //尝试打开端口
            try {
                ss = new ServerSocket(selectedPort);
                break;
            } catch (IOException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
        //请求连接
        //构造地址串
        FtpUtil.ftpWriteStringRaw(mOut, "PORT " + selectedPort);
        Socket tempSocket = null;
        try {
            tempSocket = ss.accept();
            //检查是否是来自指定node的连接
            String oAddr = tempSocket.getRemoteSocketAddress().toString();
            String nodeAddr = oAddr.substring(1, oAddr.indexOf(':'));
            if (!mHostNodeDefinition.adderss.equals(nodeAddr)) {
                FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.HOST_BADSENDCONN, ' ', "Security: Bad IP connecting.");
                try {
                    tempSocket.close();
                } catch (IOException ignored) {
                }
                return;
            }
            //save node session
            sessionRequest.mNodeSession = new HostNodeSession(tempSocket);
            sessionRequest.mNodeSession.start();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            //clean
            if (tempSocket != null) try {
                tempSocket.close();
            } catch (IOException ignored) {
            }
            FtpUtil.ftpWriteStringCommon(mOut, FtpCodes.HOST_BADSENDCONN, ' ', "Failed to establish connection.");
        } finally {
            try {
                ss.close();
            } catch (IOException ignored) {
            }
        }
    }

    private boolean doVerify() {
        FtpUtil.ftpWriteStringRaw(mOut, "CHAG");
        if (!readStatus() || mStatusCode != FtpCodes.HOST_CHALLENGEOK) {
            System.out.println("Error require challenge.");
            return false;
        }
        FtpUtil.ftpWriteStringRaw(mOut, "REPO " + mHostNodeDefinition.cert.respond(mStatusMsg));
        if (!readStatus() || mStatusCode != FtpCodes.HOST_RESPONDEOK) {
            System.out.println("Error verify challenge.");
            return false;
        }
        return true;
    }

    private void updateDirectoryMapper() {
        FtpUtil.ftpWriteStringRaw(mOut, "DMAP");
        if (!readStatus() || mStatusCode != FtpCodes.HOST_DMAP) {
            System.out.println("Error update directory mapper.");
            return;
        }
        HostDirectoryMapper.Editor e = mHostDirectoryMapper.edit(mHostNodeDefinition.number);
        boolean needCommit = false;
        while (readStatus()) {
            if (mStatusCode == FtpCodes.HOST_DMAP && "End".equals(mStatusMsg)) {
                needCommit = true;
                break;
            } else if (mStatusCode == 0) {
                e.add(mStatusMsg);
            } else {
                break;
            }
        }
        if (!needCommit) {
            e.giveup();
            System.out.println("Error update directory mapper.");
        } else {
            if (e.commit()) {
                System.out.println("Update directory mapper ok.");
            } else {
                System.out.println("Error commit directory mapper.");
            }
        }
    }

    public HostNodeSession getNodeSession(HostSession session) {
        if (Thread.currentThread().equals(this)) {
            throw new IllegalThreadStateException("Unable to call getNodeSession() at the connector's thread!");
        }

        final Object waitObj = new Object();
        final SessionRequest request = new SessionRequest(session, waitObj);

        synchronized (waitObj) {
            try {
                synchronized (mWaitObject) {
                    mSessionRequestQueue.offer(request);
                    mWaitObject.notifyAll();
                }
                waitObj.wait();
            } catch (InterruptedException ignored) {
            }
        }

        return request.mNodeSession;
    }

    private class SessionRequest {
        HostNodeSession mNodeSession = null;
        final HostSession mHostSession;
        final Object mWaitObj;

        SessionRequest(HostSession session, Object waitObj) {
            mHostSession = session;
            mWaitObj = waitObj;
        }
    }
}
