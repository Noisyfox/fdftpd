package org.foxteam.noisyfox.fdf.Host;

import org.foxteam.noisyfox.fdf.Tunables;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-21
 * Time: 下午9:49
 * To change this template use File | Settings | File Templates.
 */
public class Host {

    private final Tunables mTunables;
    private final HostDirectoryMapper mDirMapper;
    private final HashMap<Integer, HostNodeConnector> mNodeConnectorMap = new HashMap<Integer, HostNodeConnector>();

    public Host(Tunables tunables) {
        mTunables = tunables;
        mDirMapper = new HostDirectoryMapper();
    }

    public void hostStart() {
        System.out.println("Starting node connector.");
        for (HostNodeDefinition hnd : mTunables.hostNodes) {
            if (mNodeConnectorMap.containsKey(hnd.number)) {
                throw new IllegalStateException("Unable to start more than one node connector that have the same number!");
            } else {
                HostNodeConnector connector = new HostNodeConnector(hnd, mDirMapper);
                mNodeConnectorMap.put(hnd.number, connector);
                connector.start();
            }
        }
        System.out.println("Wait 5 seconds before start listening.");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {
        }

        System.out.println("Ftp server started!");
        try {
            ServerSocket s = new ServerSocket(mTunables.hostListenPort);
            while (true) {
                Socket incoming = s.accept();
                HostServant servant = new HostServant(this, incoming);
                servant.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
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
}
