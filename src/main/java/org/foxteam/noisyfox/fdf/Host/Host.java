package org.foxteam.noisyfox.fdf.Host;

import org.foxteam.noisyfox.fdf.Server;
import org.foxteam.noisyfox.fdf.Tunables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-21
 * Time: 下午9:49
 * 主机服务
 */
public class Host implements Server {
    private static final Logger log = LoggerFactory.getLogger(Host.class);

    private final Tunables mTunables;
    private final HostDirectoryMapper mDirMapper;
    private final HashMap<Integer, HostNodeConnector> mNodeConnectorMap = new HashMap<Integer, HostNodeConnector>();
    private String mHostAddress = "";

    public Host(Tunables tunables) {
        mTunables = tunables;
        mDirMapper = new HostDirectoryMapper();
    }

    public void hostStart() {
        log.info("Starting node connector.");
        for (HostNodeDefinition hnd : mTunables.hostNodes) {
            if (mNodeConnectorMap.containsKey(hnd.number)) {
                throw new IllegalStateException("Unable to start more than one node connector that have the same number!");
            } else {
                HostNodeConnector connector = new HostNodeConnector(hnd, this);
                mNodeConnectorMap.put(hnd.number, connector);
                connector.start();
            }
        }
        log.info("Wait 5 seconds before start listening.");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {
        }

        try {
            InetAddress addr = InetAddress.getLocalHost();
            mHostAddress = addr.getHostAddress();
        } catch (UnknownHostException e) {
            log.error(e.getMessage(), e);
        }

        log.info("Ftp server started!");
        try {
            ServerSocket s = new ServerSocket(mTunables.hostListenPort);
            while (true) {
                Socket incoming = s.accept();
                HostServant servant = new HostServant(this, incoming);
                servant.start();
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

    }

    public Tunables getTunables() {
        return mTunables;
    }

    public HostDirectoryMapper getDirMapper() {
        return mDirMapper;
    }

    public HostNodeConnector getHostNodeConnector(int number) {
        return mNodeConnectorMap.get(number);
    }

    public String getHostAddress() {
        return mHostAddress;
    }

    @Override
    public void startServer(String[] args) {
        hostStart();
    }

    @Override
    public void stopServer(String[] args) {
        System.exit(0);
    }
}
