package org.foxteam.nosiyfox.fdf;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-21
 * Time: 下午10:32
 * To change this template use File | Settings | File Templates.
 */
public class FtpUtil {
    public static void ftpWriteStringCommon(PrintWriter out, int status, char sep, String str) {
        StringBuilder sb = new StringBuilder(50);
        sb.append(status);
        sb.append(sep);
        String s = str.replace('\n', '\0');
        sb.append(s.replace("\377", "\377\377"));
        out.println(sb.toString());
        out.flush();
    }

    public static void ftpWriteStringRaw(PrintWriter out, String str) {
        out.println(str);
        out.flush();
    }

    public static String ftpGetRealPath(String home, String cur, String dir){

        if(dir.startsWith("~")){
            dir = dir.substring(1);
            return ftpGetRealPath(home, "", dir);
        }

        File f = new File(home);
        f = new File(f, cur);
        f = new File(f, dir);
        try {
            return f.getCanonicalPath();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return "";
    }
}
