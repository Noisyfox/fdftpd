package org.foxteam.nosiyfox.fdf;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

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

    public static String ftpGetRealPath(String home, String cur, String path) {

        if (path.startsWith("~") || path.startsWith("/") || path.startsWith("\\")) {
            path = path.substring(1);
            return ftpGetRealPath(home, "", path);
        }

        File f = new File(home);
        f = new File(f, cur);
        f = new File(f, path);
        try {
            return f.getCanonicalPath();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return "";
    }

    public static String ftpFileNameFormat(File file) {
        long modifyTime = file.lastModified();
        //判断是否是6个月以内,见http://cr.yp.to/ftp/list/binls.html
        long currentTime = System.currentTimeMillis();
        String timeString;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(currentTime);
        calendar.add(Calendar.MONTH, -6);
        long time6MonthBefore = calendar.getTimeInMillis();
        if (modifyTime < time6MonthBefore) {
            timeString = new SimpleDateFormat("MM dd yyyy")
                    .format(new Date(modifyTime));
        } else {
            timeString = new SimpleDateFormat("MM dd hh:mm")
                    .format(new Date(modifyTime));
        }

        if (file.isDirectory()) {
            return "drwxr-xr-x 1 ftp      ftp            0 "
                    + timeString + " " + file.getName();
        } else {
            return "-rw-r-r--- 1 ftp      ftp            "
                    + file.length() + " " + timeString + " "
                    + file.getName();
        }
    }
}
