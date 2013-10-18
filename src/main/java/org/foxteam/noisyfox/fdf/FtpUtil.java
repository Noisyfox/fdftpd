package org.foxteam.noisyfox.fdf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-21
 * Time: 下午10:32
 * 通用函数
 */
public class FtpUtil {
    private static final Logger log = LoggerFactory.getLogger(FtpUtil.class);
    public final static Random generator = new Random();//随机数
    public final static SimpleDateFormat dateFormatMdtm = new SimpleDateFormat("yyyyMMddhhmmss");

    public static void ftpWriteStringCommon(PrintWriter out, int status, char sep, String str) {
        StringBuilder sb = new StringBuilder(50);
        sb.append(status);
        sb.append(sep);
        sb.append(str.replace('\n', '\0').replace("\377", "\377\377"));
        sb.append("\r\n");
        String s = sb.toString();
        out.print(s);
        out.flush();

        log.debug("Result:{}", s);
    }

    public static void ftpWriteStringRaw(PrintWriter out, String str) {
        str = str.replace('\n', '\0').replace("\377", "\377\377");
        out.print(str + "\r\n");
        out.flush();

        log.debug("Result:{}", str);
    }

    public static void ftpWriteNodeString(PrintWriter out, int nodeStatus, Object... ftpMsg) {
        StringBuilder sb = new StringBuilder(50);
        sb.append(nodeStatus);
        sb.append(' ');
        for (Object s1 : ftpMsg) {
            sb.append(s1.toString().replace('\n', '\0').replace("\377", "\377\377"));
        }
        sb.append("\r\n");
        String s = sb.toString();
        out.print(s);
        out.flush();

        log.debug("Result:{}", s);
    }

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
            File tFile = path.getFile();
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
            log.error(e.getMessage(), e);
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
            log.error(e.getMessage(), e);
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
            log.error(e.getMessage(), e);
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
            log.error(e.getMessage(), e);
            return null;
        } finally {
            try {
                br.close();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
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
            log.error(e.getMessage(), e);
        }

        return number;
    }

    private static void parseStatus(String statusStr, RequestStatus status) {
        status.mStatusCode = 0;
        status.mStatusMsg = "";

        int i = statusStr.indexOf(' ');
        int i2 = statusStr.indexOf('-');
        if (i == -1) i = i2;
        else if (i2 != -1) i = Math.min(i, i2);

        if (i != -1) {
            String code = statusStr.substring(0, i).trim();
            if (!code.isEmpty()) {
                try {
                    status.mStatusCode = Integer.parseInt(code);
                } catch (NumberFormatException ignored) {
                }
            }

            status.mStatusMsg = statusStr.substring(i + 1).trim();
        }
    }

    public static boolean readStatus(Socket connection, BufferedReader reader, RequestStatus status, int timeOut, String tag) {
        try {
            connection.setSoTimeout(timeOut);
            String line = reader.readLine();
            connection.setSoTimeout(0);
            if (line != null) {
                log.debug("{};Status:{}", tag, line);
                parseStatus(line, status);
                return true;
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
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
                    log.debug("{};Command:PASS <hidden>", tag);
                } else {
                    log.debug("{};Command:{}", tag, line);
                }
                return true;
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    public static boolean readNodeRespond(Socket connection, BufferedReader reader, NodeRespond respond, int timeOut) {
        respond.mRespondCode = 0;

        try {
            connection.setSoTimeout(timeOut);
            String line = reader.readLine();
            connection.setSoTimeout(0);
            if (line != null) {
                log.debug("Node respond;{}", line);
                int i = line.indexOf(' ');
                if (i != -1) {
                    String code = line.substring(0, i).trim();
                    if (!code.isEmpty()) {
                        try {
                            respond.mRespondCode = Integer.parseInt(code);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    parseStatus(line.substring(i + 1), respond.mStatus);
                }
                return true;
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    public static ServerSocket openRandomPort() {
        //尝试开启端口监听
        int bindRetry = 10;
        int minPort = 1024;
        int maxPort = 65535;
        int selectedPort;
        while (true) {
            bindRetry--;
            if (bindRetry <= 0) {
                return null;
            }
            selectedPort = minPort + FtpUtil.generator.nextInt(maxPort - minPort) + 1;//随机端口
            //尝试打开端口
            try {
                return new ServerSocket(selectedPort);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    public static String getSocketRemoteAddress(Socket socket) {
        String oAddr = socket.getRemoteSocketAddress().toString();
        return oAddr.substring(1, oAddr.indexOf(':'));
    }

    public static boolean outPutDir(PrintWriter writer, Path dirPath, String filter, boolean fullDetails,
                                    boolean isAnon, Tunables tunables, FilePermission permission,
                                    String prefix) {
        //开始列举目录
        File[] files = FtpUtil.ftpListFileFilter(dirPath, filter);
        if (files != null) {
            if (fullDetails) {
                for (File f : files) {
                    int accessCode = 0;
                    if (isAnon) {
                        accessCode |= FilePermission.ACCESS_READ;
                        if (tunables.hostAnonUploadEnabled) {
                            accessCode |= FilePermission.ACCESS_WRITE;
                        }
                    } else {
                        accessCode = permission.getAccess(Path.valueOf(f));
                    }
                    writer.println(prefix + FtpUtil.ftpFileNameFormat(f, accessCode));
                    if (writer.checkError()) {
                        return false;
                    }
                }
            } else {
                for (File f : files) {
                    writer.println(prefix + f.getName());
                    if (writer.checkError()) {
                        return false;
                    }
                }
            }
        }
        writer.flush();
        return true;
    }

    public static String getUniqueFileName(Path path) {
        File f = path.getFile();
        String p = path.getAbsolutePath();
        int suffix = 1;
        while (f.exists()) {
            f = new File(p + "." + suffix);
            suffix++;
        }
        return f.getName();
    }

}
