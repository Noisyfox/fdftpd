package org.foxteam.noisyfox.fdf;

import org.foxteam.noisyfox.fdf.Host.HostNodeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.Vector;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-21
 * Time: 下午9:46
 * 服务器设置
 */
public class Tunables {
    private static final Logger log = LoggerFactory.getLogger(Tunables.class);
    public boolean isHost = true;
    public int serverControlPort = 2120;//控制端口
    public Path serverHome = Path.valueOf("/");

    //*************************************************************
    //host  config
    public int hostMaxClients = 2000;
    public int hostListenPort = 21;
    public int hostDataPort = 20;
    public int hostMaxLoginFails = 3;
    public int hostSessionIdleTimeout = 300 * 1000; //会话空闲超时


    public String[] hostCmdsAllowed = {}; //全局白名单
    public String[] hostCmdsDenied = {}; //全局黑名单

    public String hostRemoteCharset = "GBK";//控制流使用的默认编码
    public String hostDefaultTransferCharset = "GBK";//Ascii传输流默认使用GBK编码

    public long hostTransferRateMax = Long.MAX_VALUE;

    public String hostFtpdBanner = "";

    public boolean hostPortPromiscuous = false;
    public boolean hostPasvPromiscuous = false;
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
    public boolean hostAnonHostOnlyEmailEnabled = false;
    public String[] hostAnonHostOnlyEmail = {};
    public long hostAnonTransferRateMax = 1024 * 1024 * 3;

    public final HashMap<String, UserDefinition> hostUserDefinition = new HashMap<String, UserDefinition>();

    public HostNodeDefinition[] hostNodes = {};

    //*********************************************************
    //node config
    public int nodeControlPort = 22;

    public boolean nodePasvPromiscuous = false;
    public String nodeCertFilePath = "node.cert";
    public Vector<Pair<String, String>> nodeDirectoryMap = new Vector<Pair<String, String>>();

    public void loadFromFile(String filePath) {
        File f = new File(filePath);
        BufferedReader br;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
        } catch (FileNotFoundException e) {
            log.error(e.getMessage(), e);
            log.error("Can't open config file \"{}\" for reading! Ignored.", filePath);
            return;
        }

        log.info("Config file \"{}\" opened for reading.", filePath);

        String cfgLine;
        try {
            while ((cfgLine = br.readLine()) != null) {
                cfgLine = cfgLine.trim();
                if (cfgLine.startsWith("#") || cfgLine.isEmpty()) {
                    continue;
                }

                parseSetting(cfgLine, false);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        } finally {
            try {
                br.close();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }

    }

    private void parseHostSetting(String key, String value) {
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
            Path path = FtpUtil.ftpGetRealPath(serverHome, serverHome, Path.valueOf(value));
            String[] emails = FtpUtil.loadLinesFromFile(path, true);
            if (emails != null) {
                hostAnonDenyEmail = emails;
            }
        } else if ("anon_always_host_enable".equals(key)) {
            hostAnonHostOnlyEmailEnabled = Boolean.parseBoolean(value);
        } else if ("always_host_email_file".equals(key)) {
            Path path = FtpUtil.ftpGetRealPath(serverHome, serverHome, Path.valueOf(value));
            String[] emails = FtpUtil.loadLinesFromFile(path, true);
            if (emails != null) {
                hostAnonHostOnlyEmail = emails;
            }
        } else if ("host_listen_port".equals(key)) {
            hostListenPort = Integer.parseInt(value);
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
        } else if ("user_max_rate".equals(key)) {
            hostTransferRateMax = Long.parseLong(value);
        } else if ("port_promiscuous".equals(key)) {
            hostPortPromiscuous = Boolean.parseBoolean(value);
        } else if ("pasv_promiscuous".equals(key)) {
            hostPasvPromiscuous = Boolean.parseBoolean(value);
        } else if ("idle_timeout".equals(key)) {
            hostSessionIdleTimeout = Integer.parseInt(value);
        } else if ("user_defs".equals(key)) {
            Path userDefDirPath = FtpUtil.ftpGetRealPath(serverHome, serverHome, Path.valueOf(value));
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
                        log.info("User \"{}\" added!", ud.name);
                    } catch (RuntimeException e) {
                        log.error("Illegal user definition file \"{}\", ignored.", userDefPath.getAbsolutePath());
                    }
                }
            } else {
                log.error("Illegal user definition directory \"{}\", ignored.", value);
            }
        } else if ("host_node_count".equals(key)) {
            int count = Integer.parseInt(value);
            if (count > 0) {
                hostNodes = new HostNodeDefinition[count];
            }
        } else if (key.startsWith("host_node_address")) {
            int number = FtpUtil.getNodeNumber("host_node_address", key);
            if (number < 0 || number > hostNodes.length - 1) {
                log.error("Illegal node number, ignored.");
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
                log.error("Illegal node number, ignored.");
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
                log.error("Illegal node number, ignored.");
            } else {
                if (hostNodes[number] == null) {
                    hostNodes[number] = new HostNodeDefinition();
                    hostNodes[number].number = number;
                }
                try {
                    Path path = FtpUtil.ftpGetRealPath(serverHome, serverHome, Path.valueOf(value));
                    hostNodes[number].cert = new FtpCertification(path.getFile());
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                    log.error("Error loading cert file \"{}\".", value);
                }
            }
        } else {
            log.error("Unknown config \"{}\", ignored.", key);
        }
    }

    private void parseNodeSetting(String key, String value) {
        if ("certificate_file".equals(key)) {
            Path path = FtpUtil.ftpGetRealPath(serverHome, serverHome, Path.valueOf(value));
            nodeCertFilePath = path.getAbsolutePath();
        } else if ("remote_port".equals(key)) {
            nodeControlPort = Integer.parseInt(value);
        } else if ("pasv_promiscuous".equals(key)) {
            nodePasvPromiscuous = Boolean.parseBoolean(value);
        } else if ("dir_map_file".equals(key)) {
            Path path = FtpUtil.ftpGetRealPath(serverHome, serverHome, Path.valueOf(value));
            String[] maps = FtpUtil.loadLinesFromFile(path, true);
            if (maps != null) {
                for (String s : maps) {
                    int firstDoubleColonIndex = s.indexOf("::");
                    if (firstDoubleColonIndex == -1 || firstDoubleColonIndex >= s.length() - 2) {
                        log.error("Illegal dir map \"{}\", ignored.", s);
                        continue;
                    }
                    String from = s.substring(0, firstDoubleColonIndex).trim();
                    String to = s.substring(firstDoubleColonIndex + 2).trim();
                    nodeDirectoryMap.add(new Pair<String, String>(from, to));
                }
            }
        } else {
            log.error("Unknown config \"{}\", ignored.", key);
        }
    }

    /**
     * parseSetting()
     * Handle a given name=value setting and apply it.
     *
     * @param line      the name=value pair to apply
     * @param forceHost whether is host config or auto
     */
    public void parseSetting(String line, boolean forceHost) {
        String key;
        String value;
        {
            int firstEqualsIndex = line.indexOf('=');
            if (firstEqualsIndex == -1 || firstEqualsIndex + 1 >= line.length()) {
                log.error("Error parsing line \"{}\", ignored.", line);
                return;
            } else {
                key = line.substring(0, firstEqualsIndex).trim();
                value = line.substring(firstEqualsIndex + 1).trim();
            }
        }

        if (key.isEmpty() || value.isEmpty()) {
            return;
        }

        try {
            if ("service_type".equals(key)) {
                if ("HOST".equals(value)) {
                    isHost = true;
                } else if ("NODE".equals(value)) {
                    isHost = false;
                } else {
                    log.error("Bad service type \"{}\", ignored.", value);
                }
            } else if ("server_control_port".equals(key)) {
                serverControlPort = Integer.parseInt(value);
            } else if (forceHost || isHost) {
                parseHostSetting(key, value);
            } else {
                parseNodeSetting(key, value);
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            log.error("Error parse config \"{}\", ignored.", line);
        }
    }

}
