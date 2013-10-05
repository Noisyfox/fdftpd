package org.foxteam.noisyfox.fdf;

import org.foxteam.noisyfox.fdf.Host.HostNodeDefinition;

import java.io.*;
import java.util.HashMap;
import java.util.Vector;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-21
 * Time: 下午9:46
 * To change this template use File | Settings | File Templates.
 */
public class Tunables {
    public boolean isHost = true;

    //*************************************************************
    //host  config
    public int hostMaxClients = 2000;
    public int hostListenPort = 21;
    public int hostDataPort = 20;
    public int hostMaxLoginFails = 3;


    public String[] hostCmdsAllowed = {}; //全局白名单
    public String[] hostCmdsDenied = {}; //全局黑名单

    public String hostRemoteCharset = "GBK";//控制流使用的默认编码
    public String hostDefaultTransferCharset = "GBK";//Ascii传输流默认使用GBK编码

    public long hostTransferRateMax = Long.MAX_VALUE;

    public String hostFtpdBanner = "";

    public boolean hostPasvEnabled = true;
    public boolean hostPortEnabled = true;
    public boolean hostWriteEnabled = false;
    public boolean hostDownloadEnabled = true;
    public boolean hostDirListEnabled = true;
    public boolean hostChmodEnabled = true;
    public boolean hostAsciiUploadEnabled = false;
    public boolean hostAsciiDownloadEnabled = false;

    public boolean hostAnonEnabled = true;
    public Path hostAnonHome = Path.valueOf("/");
    public boolean hostAnonNoPassword = false;
    public boolean hostAnonUploadEnabled = false;
    public boolean hostAnonMkdirWriteEnabled = false;
    public boolean hostAnonOtherWriteEnabled = false;
    public boolean hostAnonDenyEmailEnabled = false;
    public String[] hostAnonDenyEmail = {};
    public long hostAnonTransferRateMax = 1024 * 1024 * 3;

    public final HashMap<String, UserDefinition> hostUserDefinition = new HashMap<String, UserDefinition>();

    public HostNodeDefinition[] hostNodes = {};

    //*********************************************************
    //node config
    public int nodeControlPort = 22;

    public String nodeCertFilePath = "node.cert";
    public Vector<Pair<String, String>> nodeDirectoryMap = new Vector<Pair<String, String>>();

    public void loadFromFile(String filePath) {
        File f = new File(filePath);
        BufferedReader br;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.out.println("Can't open config file \"" + filePath + "\" for reading! Ignored.");
            return;
        }

        System.out.println("Config file \"" + filePath + "\" opened for reading.");

        String cfgLine;
        try {
            while ((cfgLine = br.readLine()) != null) {
                cfgLine = cfgLine.trim();
                if (cfgLine.startsWith("#") || cfgLine.isEmpty()) {
                    continue;
                }

                String key;
                String value;
                {
                    int firstEqualsIndex = cfgLine.indexOf('=');
                    if (firstEqualsIndex == -1 || firstEqualsIndex + 1 >= cfgLine.length()) {
                        System.out.println("Error parsing line \"" + cfgLine + "\", ignored.");
                        continue;
                    } else {
                        key = cfgLine.substring(0, firstEqualsIndex).trim();
                        value = cfgLine.substring(firstEqualsIndex + 1).trim();
                    }
                }

                if (key.isEmpty() || value.isEmpty()) {
                    continue;
                }

                if ("service_type".equals(key)) {
                    if ("HOST".equals(value)) {
                        isHost = true;
                    } else if ("NODE".equals(value)) {
                        isHost = false;
                    } else {
                        System.out.println("Bad service type \"" + value + "\", ignored.");
                    }
                } else if (isHost) {
                    if ("anonymous_enable".equals(key)) {
                        hostAnonEnabled = Boolean.parseBoolean(value);
                    } else if ("anon_home".equals(key)) {
                        hostAnonHome = Path.valueOf(value);
                    } else if ("anon_no_password".equals(key)) {
                        hostAnonNoPassword = Boolean.parseBoolean(value);
                    } else if ("write_enable".equals(key)) {
                        hostWriteEnabled = Boolean.parseBoolean(value);
                    } else if ("anon_upload_enable".equals(key)) {
                        hostAnonUploadEnabled = Boolean.parseBoolean(value);
                    } else if ("anon_mkdir_write_enable".equals(key)) {
                        hostAnonMkdirWriteEnabled = Boolean.parseBoolean(value);
                    } else if ("anon_other_write_enable".equals(key)) {
                        hostAnonOtherWriteEnabled = Boolean.parseBoolean(value);
                    } else if ("ascii_upload_enable".equals(key)) {
                        hostAsciiUploadEnabled = Boolean.parseBoolean(value);
                    } else if ("ascii_download_enable".equals(key)) {
                        hostAsciiDownloadEnabled = Boolean.parseBoolean(value);
                    } else if ("ftpd_banner".equals(key)) {
                        hostFtpdBanner = value;
                    } else if ("deny_email_enable".equals(key)) {
                        hostAnonDenyEmailEnabled = Boolean.parseBoolean(value);
                    } else if ("banned_email_file".equals(key)) {
                        String[] emails = FtpUtil.loadLinesFromFile(Path.valueOf(value), true);
                        if (emails != null) {
                            hostAnonDenyEmail = emails;
                        }
                    } else if ("ascii_charset".equals(key)) {
                        hostDefaultTransferCharset = value;
                    } else if ("remote_charset".equals(key)) {
                        hostRemoteCharset = value;
                    } else if ("cmds_allowed".equals(key)) {
                        hostCmdsAllowed = value.split(",");
                    } else if ("cmds_denied".equals(key)) {
                        hostCmdsDenied = value.split(",");
                    } else if ("max_clients".equals(key)) {
                        hostMaxClients = Integer.parseInt(value);
                    } else if ("anon_max_rate".equals(key)) {
                        hostAnonTransferRateMax = Long.parseLong(value);
                    } else if ("user_defs".equals(key)) {
                        Path userDefDirPath = Path.valueOf(value);
                        File userDefDirFile = userDefDirPath.getFile();
                        if (userDefDirFile.isDirectory()) {
                            String[] userDefs = userDefDirFile.list(new FilenameFilter() {
                                @Override
                                public boolean accept(File dir, String name) {
                                    return name.endsWith(".usr");
                                }
                            });
                            for (String userDef : userDefs) {
                                Path userDefPath = userDefDirPath.link(userDef);
                                UserDefinition ud = new UserDefinition();
                                try {
                                    ud.loadFromFile(userDefPath);
                                    hostUserDefinition.put(ud.name, ud);
                                    System.out.println("User \"" + ud.name + "\" added!");
                                } catch (RuntimeException e) {
                                    System.out.println("Illegal user definition file \"" + userDefPath.getAbsolutePath() + "\", ignored.");
                                }
                            }
                        } else {
                            System.out.println("Illegal user definition directory \"" + value + "\", ignored.");
                        }
                    } else if ("host_node_count".equals(key)) {
                        int count = Integer.parseInt(value);
                        if (count > 0) {
                            hostNodes = new HostNodeDefinition[count];
                        }
                    } else if (key.startsWith("host_node_address")) {
                        int number = FtpUtil.getNodeNumber("host_node_address", key);
                        if (number < 0 || number > hostNodes.length - 1) {
                            System.out.println("Illegal node number, ignored.");
                        } else {
                            if (hostNodes[number] == null) {
                                hostNodes[number] = new HostNodeDefinition();
                                hostNodes[number].number = number;
                            }
                            hostNodes[number].adderss = value;
                        }
                    } else if (key.startsWith("host_node_port")) {
                        int number = FtpUtil.getNodeNumber("host_node_port", key);
                        if (number < 0 || number > hostNodes.length - 1) {
                            System.out.println("Illegal node number, ignored.");
                        } else {
                            if (hostNodes[number] == null) {
                                hostNodes[number] = new HostNodeDefinition();
                                hostNodes[number].number = number;
                            }
                            hostNodes[number].port = Integer.parseInt(value);
                        }
                    } else if (key.startsWith("host_node_cert")) {
                        int number = FtpUtil.getNodeNumber("host_node_cert", key);
                        if (number < 0 || number > hostNodes.length - 1) {
                            System.out.println("Illegal node number, ignored.");
                        } else {
                            if (hostNodes[number] == null) {
                                hostNodes[number] = new HostNodeDefinition();
                                hostNodes[number].number = number;
                            }
                            try {
                                hostNodes[number].cert = new FtpCertification(value);
                            } catch (IOException e) {
                                e.printStackTrace();
                                System.out.println("Error loading cert file \"" + value + "\".");
                            }
                        }
                    } else {
                        System.out.println("Unknown config \"" + key + "\", ignored.");
                    }
                } else {
                    if ("certificate_file".equals(key)) {
                        nodeCertFilePath = value;
                    } else if ("remote_port".equals(key)) {
                        nodeControlPort = Integer.parseInt(value);
                    } else if ("dir_map_file".equals(key)) {
                        String[] maps = FtpUtil.loadLinesFromFile(Path.valueOf(value), true);
                        if (maps != null) {
                            for (String s : maps) {
                                int firstDoubleColonIndex = s.indexOf("::");
                                if (firstDoubleColonIndex == -1 || firstDoubleColonIndex >= s.length() - 2) {
                                    System.out.println("Illegal dir map \"" + s + "\", ignored.");
                                    continue;
                                }
                                String from = s.substring(0, firstDoubleColonIndex).trim();
                                String to = s.substring(firstDoubleColonIndex + 2).trim();
                                nodeDirectoryMap.add(new Pair<String, String>(from, to));
                            }
                        }
                    } else {
                        System.out.println("Unknown config \"" + key + "\", ignored.");
                    }
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

}
