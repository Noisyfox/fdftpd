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
    public final FilePermission permission = new FilePermission();

    public void loadFromFile(Path filePath) throws RuntimeException {
        String[] lines = FtpUtil.loadLinesFromFile(filePath, true);
        if (lines.length < 1) {
            throw new RuntimeException("Illegal user config file!");
        }
        //第一行是用户定义
        String user = lines[0];
        String[] userLineSplit = user.split("::");
        if (userLineSplit.length < 4) {
            throw new RuntimeException("Illegal user definition: \"" + user + "\"");
        }
        name = userLineSplit[0];
        passwd = userLineSplit[1];
        uid = Integer.parseInt(userLineSplit[2]);
        home = Path.valueOf(userLineSplit[3]);
        //开始定义权限
        //先增加默认权限
        permission.addPermission(false, true, false, home, '*');
        permission.addPermission(true, true, false, home, '*');
        for (int i = 1; i < lines.length; i++) {
            boolean isDir, canRead, canWrite;
            String[] permissionLineSplit = lines[i].split("::");
            char[] perItems = permissionLineSplit[0].toLowerCase().toCharArray();
            if (perItems.length < 3) {
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
            Path perPath = home;
            char suffix = '*';
            if (permissionLineSplit.length > 2) {
                perPath = Path.valueOf(permissionLineSplit[1]);
            }
            if (permissionLineSplit.length > 3) {
                char c = permissionLineSplit[2].charAt(0);
                if (c == '-' || c == '?' || c == '*') {
                    suffix = c;
                } else {
                    throw new RuntimeException("Illegal permission definition: \"" + lines[i] + "\"");
                }
            }
            permission.addPermission(isDir, canRead, canWrite, perPath, suffix);
        }
    }
}
