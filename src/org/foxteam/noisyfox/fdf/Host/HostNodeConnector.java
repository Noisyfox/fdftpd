package org.foxteam.noisyfox.fdf.Host;

import org.foxteam.noisyfox.fdf.FtpCodes;
import org.foxteam.noisyfox.fdf.FtpUtil;
import org.foxteam.noisyfox.fdf.RequestStatus;
import org.foxteam.noisyfox.fdf.Tunables;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-28
 * Time: 下午2:17
 * To change this template use File | Settings | File Templates.
 */
public class HostNodeConnector extends Thread {
    private final Object mWaitObject = new Object();
    private final Host mHost;
    protected final HostNodeDefinition mHostNodeDefinition;
    private final HostDirectoryMapper mHostDirectoryMapper;
    private final Queue<SessionRequest> mSessionRequestQueue = new LinkedList<SessionRequest>();
    protected Socket mConnecting;
    protected PrintWriter mOut;
    protected BufferedReader mIn;

    RequestStatus mNodeStatus = new RequestStatus();

    public HostNodeConnector(HostNodeDefinition nodeDef, Host host) {
        mHost = host;
        mHostNodeDefinition = nodeDef;
        mHostDirectoryMapper = host.getDirMapper();
    }

    private boolean readStatus() {
        return FtpUtil.readStatus(mConnecting, mIn, mNodeStatus, 20000, "Node");
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
        //synchronize host settings
        uploadHostConfig();

        //主逻辑
        while (true) {
            synchronized (mWaitObject) {
                FtpUtil.ftpWriteStringRaw(mOut, "NOOP");//发送心跳
                if (!readStatus() || mNodeStatus.mStatusCode != FtpCodes.FTP_NOOPOK) { //挂了
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
            selectedPort = minPort + FtpUtil.generator.nextInt(maxPort - minPort) + 1;//随机端口
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
            HostNodeSession session = new HostNodeSession(this, tempSocket);
            if (session.prepareConnection(sessionRequest.mHostServant)) {
                sessionRequest.mNodeSession = session;
            }
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
        if (!readStatus() || mNodeStatus.mStatusCode != FtpCodes.HOST_CHALLENGEOK) {
            System.out.println("Error require challenge.");
            return false;
        }
        FtpUtil.ftpWriteStringRaw(mOut, "REPO " + mHostNodeDefinition.cert.respond(mNodeStatus.mStatusMsg));
        if (!readStatus() || mNodeStatus.mStatusCode != FtpCodes.HOST_RESPONDEOK) {
            System.out.println("Error verify challenge.");
            return false;
        }
        return true;
    }

    private void updateDirectoryMapper() {
        FtpUtil.ftpWriteStringRaw(mOut, "DMAP");
        if (!readStatus() || mNodeStatus.mStatusCode != FtpCodes.HOST_DMAP) {
            System.out.println("Error update directory mapper.");
            return;
        }
        HostDirectoryMapper.Editor e = mHostDirectoryMapper.edit(mHostNodeDefinition.number);
        boolean needCommit = false;
        while (readStatus()) {
            if (mNodeStatus.mStatusCode == FtpCodes.HOST_DMAP && "End".equals(mNodeStatus.mStatusMsg)) {
                needCommit = true;
                break;
            } else if (mNodeStatus.mStatusCode == 0) {
                e.add(mNodeStatus.mStatusMsg);
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

    private void uploadHostConfig() {
        Tunables hostTunables = mHost.getTunables();
        Object[][] data = {{"anon_max_rate", hostTunables.hostAnonTransferRateMax},
                {"user_max_rate", hostTunables.hostTransferRateMax},
                {"ascii_charset", hostTunables.hostDefaultTransferCharset}};

        for (Object[] cfg : data) {
            if (cfg == null || cfg.length < 2) {
                continue;
            }

            FtpUtil.ftpWriteStringRaw(mOut, "CONF " + cfg[0] + '=' + cfg[1]);
            if (!readStatus() || mNodeStatus.mStatusCode != FtpCodes.HOST_CONFOK) {
                System.out.println("Error upload host config.");
                return;
            }
        }
        System.out.println("Host config uploaded!");
    }

    public HostNodeSession getNodeSession(HostServant servant) {
        if (Thread.currentThread().equals(this)) {
            throw new IllegalThreadStateException("Unable to call getNodeSession() at the connector's thread!");
        }

        final Object waitObj = new Object();
        final SessionRequest request = new SessionRequest(servant, waitObj);

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
        final HostServant mHostServant;
        final Object mWaitObj;

        SessionRequest(HostServant servant, Object waitObj) {
            mHostServant = servant;
            mWaitObj = waitObj;
        }
    }
}
