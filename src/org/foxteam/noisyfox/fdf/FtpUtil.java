package org.foxteam.noisyfox.fdf;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Vector;
import java.util.regex.Pattern;

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
        System.out.println("Result:" + sb.toString());
        out.flush();
    }

    public static void ftpWriteStringRaw(PrintWriter out, String str) {
        out.println(str);
        System.out.println("Result:" + str);
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

    private static SimpleDateFormat dateFormatYear = new SimpleDateFormat("MMM dd yyyy", Locale.ENGLISH);
    private static SimpleDateFormat dateFormatTime = new SimpleDateFormat("MMM dd hh:mm", Locale.ENGLISH);

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
            timeString = dateFormatYear.format(new Date(modifyTime));
        } else {
            timeString = dateFormatTime.format(new Date(modifyTime));
        }

        if (file.isDirectory()) {
            return String.format("d%s%s%sr-xr-x 1 0      0   0 %s %s",
                    file.canRead() ? "r" : "-", file.canWrite() ? "w" : "-", file.canExecute() ? "x" : "-",
                    timeString, file.getName());
        } else {
            return String.format("-%s%s%sr-xr-x 1 0      0   %d %s %s",
                    file.canRead() ? "r" : "-", file.canWrite() ? "w" : "-", file.canExecute() ? "x" : "-",
                    file.length(), timeString, file.getName());
        }
    }

    public static File[] ftpListFileFilter(String path, String filter) {
        try {
            //找到第一个最后一级不包含通配符的路径
            filter = filter.replace('\\', '/');//全部转成/分割
            int firstWildcard1 = filter.indexOf('?');
            int firstWildcard2 = filter.indexOf('*');
            int firstWildcard;
            if (firstWildcard1 == -1 && firstWildcard2 == -1) {
                firstWildcard = -1;
            } else if (firstWildcard1 == -1) {
                firstWildcard = firstWildcard2;
            } else if (firstWildcard2 == -1) {
                firstWildcard = firstWildcard1;
            } else {
                firstWildcard = Math.min(firstWildcard1, firstWildcard2);
            }
            int lastSplashBeforeWildcard = filter.lastIndexOf('/', firstWildcard);
            String newPath;
            if (lastSplashBeforeWildcard == -1) {
                newPath = "";
            } else {
                newPath = filter.substring(0, lastSplashBeforeWildcard);
                if (lastSplashBeforeWildcard + 1 >= filter.length()) {
                    filter = "";
                } else {
                    filter = filter.substring(lastSplashBeforeWildcard + 1);
                }
            }

            newPath = FtpUtil.ftpGetRealPath(path, "", newPath);
            File tFile = new File(newPath);
            if (filter.isEmpty()) {
                if (!tFile.exists() || !tFile.canRead()) return null;
                if (tFile.isFile()) return new File[]{tFile};
                else {
                    return tFile.listFiles();
                }
            } else {
                if (!tFile.exists() || !tFile.canRead() || tFile.isFile()) return null;
                File[] fList = tFile.listFiles();
                if (fList == null) return null;
                //转义
                filter = filter.replace(".", "\\.").replace("^", "\\^").replace("$", "\\$")
                        .replace("+", "\\+").replace("{", "\\{").replace("}", "\\}").replace("[", "\\[")
                        .replace("]", "\\]").replace("|", "\\|").replace("(", "\\(").replace(")", "\\)")
                        .replace("?", ".").replace("*", ".*");
                Pattern pattern = Pattern.compile(filter);
                Vector<File> matchedFiles = new Vector<File>(fList.length);
                for (File f : fList) {
                    if (pattern.matcher(f.getName()).matches()) {
                        matchedFiles.add(f);
                    }
                }
                File[] mf = new File[matchedFiles.size()];
                matchedFiles.toArray(mf);
                return mf;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
