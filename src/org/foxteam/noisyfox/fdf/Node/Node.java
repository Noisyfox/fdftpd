package org.foxteam.noisyfox.fdf.Node;

import org.foxteam.noisyfox.fdf.FtpCertification;
import org.foxteam.noisyfox.fdf.Pair;
import org.foxteam.noisyfox.fdf.Tunables;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-25
 * Time: 下午7:56
 * To change this template use File | Settings | File Templates.
 */
public class Node {

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
        System.out.println("Loading certification file: " + mTunables.nodeCertFilePath);
        try {
            cert = new FtpCertification(mTunables.nodeCertFilePath);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Load certification file failed!");
            return;
        }
        //load database/prepare filesystem
        //perpare dir map
        for (Pair<String, String> def : mTunables.nodeDirectoryMap) {
            mDirectoryMapper.addPathMap(def.getValue1(), def.getValue2());
        }

        //waiting for host to connect
        System.out.println("Waiting for host to connect");
        try {
            ServerSocket s = new ServerSocket(mTunables.nodeControlPort);
            while (true) {
                Socket incoming = s.accept();
                NodeCenter center = new NodeCenter(mDirectoryMapper, cert, mTunables, incoming);
                System.out.println("Node session start!");
                try {
                    center.startNode();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    center.tryDoClean();
                }
                System.out.println("Node session exit!");
                // Oops! Seems it's not a connection from Host or connection lost

                try {
                    incoming.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
