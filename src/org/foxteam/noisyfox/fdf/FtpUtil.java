package org.foxteam.noisyfox.fdf;

import java.io.*;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    /*
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
     */

    public static Path ftpGetRealPath(Path home, Path cur, Path path) {
        int rela = path.getRelativity();
        Path rp = null;
        switch (rela) {
            case Path.RELA_CURR:
                rp = cur.link(path);
                break;
            case Path.RELA_HOME:
            case Path.RELA_ROOT:
                rp = home.link(path);
                break;
        }
        return rp;
    }

    private static SimpleDateFormat dateFormatYear = new SimpleDateFormat("MMM dd yyyy", Locale.ENGLISH);
    private static SimpleDateFormat dateFormatTime = new SimpleDateFormat("MMM dd hh:mm", Locale.ENGLISH);

    public static String ftpFileNameFormat(File file, int fileAccess) {
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
                    (fileAccess & FilePermission.ACCESS_READ) != 0 ? "r" : "-",
                    (fileAccess & FilePermission.ACCESS_WRITE) != 0 ? "w" : "-",
                    (fileAccess & FilePermission.ACCESS_EXECUTE) != 0 ? "x" : "-",
                    timeString, file.getName());
        } else {
            return String.format("-%s%s%sr-xr-x 1 0      0   %d %s %s",
                    (fileAccess & FilePermission.ACCESS_READ) != 0 ? "r" : "-",
                    (fileAccess & FilePermission.ACCESS_WRITE) != 0 ? "w" : "-",
                    (fileAccess & FilePermission.ACCESS_EXECUTE) != 0 ? "x" : "-",
                    file.length(), timeString, file.getName());
        }
    }

    public static File[] ftpListFileFilter(Path path, String filter) {
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

            Path targetPath = FtpUtil.ftpGetRealPath(path, Path.valueOf("."), Path.valueOf(newPath));
            File tFile = targetPath.getFile();
            if (filter.isEmpty()) {
                if (!tFile.exists() || !tFile.canRead()) return null;
                if (tFile.isFile()) return new File[]{tFile};
                else {
                    return tFile.listFiles();
                }
            } else {
                if (!tFile.exists() || !tFile.canRead() || tFile.isFile()) return null;
                //转义
                filter = filter.replace(".", "\\.").replace("^", "\\^").replace("$", "\\$")
                        .replace("+", "\\+").replace("{", "\\{").replace("}", "\\}").replace("[", "\\[")
                        .replace("]", "\\]").replace("|", "\\|").replace("(", "\\(").replace(")", "\\)")
                        .replace("?", ".").replace("*", ".*");
                final Pattern pattern = Pattern.compile(filter);
                return tFile.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return pattern.matcher(name).matches();
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String hashString(String data, String algorithm) {
        if (data == null)
            return null;

        return hashBytes(data.getBytes(), algorithm);
    }

    public static String hashBytes(byte[] data, String algorithm) {
        if (data == null)
            return null;
        try {
            MessageDigest mdInst = MessageDigest.getInstance(algorithm);
            mdInst.update(data);
            byte md[] = mdInst.digest();
            StringBuilder sb = new StringBuilder(64);
            for (byte b : md) {
                sb.append(String.format("%02X", b));
            }

            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String[] loadLinesFromFile(Path path, boolean ignoreComment) {
        Vector<String> strings = new Vector<String>();
        File f = path.getFile();
        BufferedReader br;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        String cfgLine;
        try {
            if (ignoreComment) {
                while ((cfgLine = br.readLine()) != null) {
                    if (cfgLine.isEmpty() || cfgLine.startsWith("#")) continue;
                    strings.add(cfgLine.trim());
                }
            } else {
                while ((cfgLine = br.readLine()) != null) {
                    strings.add(cfgLine.trim());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        String[] array = new String[strings.size()];
        strings.toArray(array);
        return array;
    }

    public static int getNodeNumber(String prefix, String value) {
        int preLen = prefix.length();
        if (preLen >= value.length()) {
            return -1;
        }
        String v = value.substring(preLen);
        int number = -1;

        try {
            number = Integer.parseInt(v);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        return number;
    }

    public static boolean readStatus(Socket connection, BufferedReader reader, RequestStatus status, int timeOut, String tag) {
        status.mStatusCode = 0;
        status.mStatusMsg = "";
        try {
            connection.setSoTimeout(timeOut);
            String line = reader.readLine();
            connection.setSoTimeout(0);
            if (line != null) {
                System.out.println(tag + ";Status:" + line);
                int i = line.indexOf(' ');
                int i2 = line.indexOf('-');
                if (i == -1) i = i2;
                else if (i2 != -1) i = Math.min(i, i2);

                if (i != -1) {
                    String code = line.substring(0, i).trim();
                    if (!code.isEmpty()) {
                        try {
                            status.mStatusCode = Integer.parseInt(code);
                        } catch (NumberFormatException ignored) {
                        }
                    }

                    if (i + 1 >= line.length() - 1) {
                        status.mStatusMsg = "";
                    } else {
                        status.mStatusMsg = line.substring(i + 1).trim();
                    }
                }
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean readCmdArg(Socket connection, BufferedReader reader, RequestCmdArg cmdArg, int timeOut, String tag) {
        cmdArg.mCmd = "";
        cmdArg.mArg = "";
        try {
            connection.setSoTimeout(timeOut);
            String line = reader.readLine();
            connection.setSoTimeout(0);
            if (line != null) {
                int i = line.indexOf(' ');
                if (i != -1) {
                    cmdArg.mCmd = line.substring(0, i).trim().toUpperCase();
                    cmdArg.mArg = line.substring(i).trim();
                } else {
                    cmdArg.mCmd = line;
                }
                if ("PASS".equals(cmdArg.mCmd)) {
                    System.out.println(tag + ";Command:PASS <hidden>");
                } else {
                    System.out.println(tag + ";Command:" + line);
                }
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return false;
    }

}
