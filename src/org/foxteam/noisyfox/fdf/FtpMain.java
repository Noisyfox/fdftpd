package org.foxteam.noisyfox.fdf;

import org.foxteam.noisyfox.fdf.Host.Host;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-21
 * Time: 下午9:41
 * To change this template use File | Settings | File Templates.
 */
public class FtpMain {
    public static final String FDF_VER = "0.1";
    public static final int IPPORT_RESERVED = 1024;

    public static void main(String args[]){

        Tunables tunables = new Tunables();

        for(int i = 1; i < args.length; i++){
            String arg = args[i];
            if(arg.startsWith("-")){//设置

            }else{//配置文件
                tunables.loadFromFile(arg);
            }
        }

        if(tunables.isHost){
            Host h = new Host(tunables);
            h.hostStart();
        }else{

        }

    }
}
