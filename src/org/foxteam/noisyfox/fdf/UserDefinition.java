package org.foxteam.noisyfox.fdf;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-10-4
 * Time: 下午10:57
 * To change this template use File | Settings | File Templates.
 */
public class UserDefinition {
    public String name = "";
    public String passwd = "";
    public int uid = -1;
    public Path home = null;
    public String[] cmdsAllowed = {};
    public String[] cmdsDenied = {};

    public final FilePermission permission = new FilePermission();

    public void loadFromFile(Path filePath) throws RuntimeException {
        String[] lines = FtpUtil.loadLinesFromFile(filePath, true);
        if (lines.length < 1) {
            throw new RuntimeException("Illegal user config file!");
        }
        //第一行是用户定义
        String user = lines[0];
        String[] userLineSplit = user.split("::");
        if (userLineSplit.length < 6) {
            throw new RuntimeException("Illegal user definition: \"" + user + "\"");
        }
        userLineSplit[0] = userLineSplit[0].trim();
        userLineSplit[1] = userLineSplit[1].trim();
        userLineSplit[2] = userLineSplit[2].trim();
        userLineSplit[3] = userLineSplit[3].trim();
        userLineSplit[4] = userLineSplit[4].trim();
        userLineSplit[5] = userLineSplit[5].trim();

        name = userLineSplit[0];
        passwd = userLineSplit[1];
        uid = Integer.parseInt(userLineSplit[2]);
        home = Path.valueOf(userLineSplit[3]);
        if (!userLineSplit[4].isEmpty()) {
            cmdsAllowed = userLineSplit[4].split(",");
        }
        if (!userLineSplit[5].isEmpty()) {
            cmdsDenied = userLineSplit[5].split(",");
        }

        //开始定义权限
        //先增加默认权限
        permission.addPermission(false, true, false, false, home, '*');
        permission.addPermission(true, true, false, true, home, '*');
        permission.addPermission(true, true, false, true, home, '-');
        for (int i = 1; i < lines.length; i++) {
            boolean isDir, canRead, canWrite, canExecute;
            String[] permissionLineSplit = lines[i].split("::");
            char[] perItems = permissionLineSplit[0].toLowerCase().toCharArray();
            if (perItems.length < 4) {
                throw new RuntimeException("Illegal permission definition: \"" + lines[i] + "\"");
            }
            if (perItems[0] == '-') {
                isDir = false;
            } else if (perItems[0] == 'd') {
                isDir = true;
            } else {
                throw new RuntimeException("Illegal permission definition: \"" + lines[i] + "\"");
            }
            if (perItems[1] == '-') {
                canRead = false;
            } else if (perItems[1] == 'r') {
                canRead = true;
            } else {
                throw new RuntimeException("Illegal permission definition: \"" + lines[i] + "\"");
            }
            if (perItems[2] == '-') {
                canWrite = false;
            } else if (perItems[2] == 'w') {
                canWrite = true;
            } else {
                throw new RuntimeException("Illegal permission definition: \"" + lines[i] + "\"");
            }
            if (perItems[3] == '-') {
                canExecute = false;
            } else if (perItems[3] == 'x') {
                canExecute = true;
            } else {
                throw new RuntimeException("Illegal permission definition: \"" + lines[i] + "\"");
            }
            if (permissionLineSplit.length == 1) {//默认设置
                if (isDir) {
                    permission.addPermission(isDir, canRead, canWrite, canExecute, home, '-');
                }
            }
            Path perPath = home;
            char suffix = '*';
            if (permissionLineSplit.length > 1) {
                perPath = Path.valueOf(permissionLineSplit[1]);
            }
            if (permissionLineSplit.length > 2) {
                char c = permissionLineSplit[2].charAt(0);
                if (c == '-' || c == '?' || c == '*') {
                    suffix = c;
                } else {
                    throw new RuntimeException("Illegal permission definition: \"" + lines[i] + "\"");
                }
            }
            permission.addPermission(isDir, canRead, canWrite, canExecute, perPath, suffix);
        }
    }
}
