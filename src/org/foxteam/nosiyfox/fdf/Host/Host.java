package org.foxteam.nosiyfox.fdf.Host;

import org.foxteam.nosiyfox.fdf.Tunables;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-21
 * Time: 下午9:49
 * To change this template use File | Settings | File Templates.
 */
public class Host {

    private final Tunables mTunables;

    public Host(Tunables tunables){
        mTunables = tunables;
    }

    public void hostStart(){
        System.out.println("Ftp server started!");

        try {
            ServerSocket s = new ServerSocket(mTunables.hostListenPort);
            while(true){
                Socket incoming = s.accept();
                HostServant servant = new HostServant(mTunables, incoming);
                servant.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
