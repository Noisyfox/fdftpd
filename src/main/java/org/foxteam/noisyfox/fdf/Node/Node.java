package org.foxteam.noisyfox.fdf.Node;

import org.foxteam.noisyfox.fdf.FtpCertification;
import org.foxteam.noisyfox.fdf.Pair;
import org.foxteam.noisyfox.fdf.Server;
import org.foxteam.noisyfox.fdf.Tunables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-25
 * Time: 下午7:56
 * 主节点服务
 */
public class Node implements Server {
    private static final Logger log = LoggerFactory.getLogger(Node.class);

    private final Tunables mTunables;

    private final NodeDirectoryMapper mDirectoryMapper = new NodeDirectoryMapper();

    public Node(Tunables tunables) {
        mTunables = tunables;
    }

    public void nodeStart() {
        //prepare node to work
        //load node specific config
        //load certificate
        FtpCertification cert;
        log.info("Loading certification file: {}", mTunables.nodeCertFilePath);
        try {
            cert = new FtpCertification(mTunables.nodeCertFilePath);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            log.error("Load certification file failed!");
            return;
        }
        //load database/prepare filesystem
        //perpare dir map
        for (Pair<String, String> def : mTunables.nodeDirectoryMap) {
            mDirectoryMapper.addPathMap(def.getValue1(), def.getValue2());
        }

        //waiting for host to connect
        log.info("Waiting for host to connect");
        try {
            ServerSocket s = new ServerSocket(mTunables.nodeControlPort);
            while (true) {
                Socket incoming = s.accept();
                NodeCenter center = new NodeCenter(mDirectoryMapper, cert, mTunables, incoming);
                log.info("Node session start!");
                try {
                    center.startNode();
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                } finally {
                    center.tryDoClean();
                }
                log.error("Node session exit!");
                // Oops! Seems it's not a connection from Host or connection lost

                try {
                    incoming.close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void startServer(String[] args) {
        nodeStart();
    }

    @Override
    public void stopServer(String[] args) {
        System.exit(0);
    }
}
