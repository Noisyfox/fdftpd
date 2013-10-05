package org.foxteam.noisyfox.fdf;

import java.util.LinkedList;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-10-5
 * Time: 下午12:27
 * To change this template use File | Settings | File Templates.
 */
public class FilePermission {
    public static final int ACCESS_READ = 0x1;
    public static final int ACCESS_WRITE = 0x2;
    public static final int ACCESS_EXECUTE = 0x4;
    public static final int ACCESS_DIRECTORY = 0x8;
    public static final int ACCESS_INHERIT = 0x10;

    public static final int OPERATION_FILE_READ = 1;
    public static final int OPERATION_FILE_WRITE = 2;
    public static final int OPERATION_FILE_CREATE = 3;
    public static final int OPERATION_FILE_DELETE = 4;
    public static final int OPERATION_FILE_RENAME_FROM = 5;
    public static final int OPERATION_FILE_RENAME_TO = 6;
    public static final int OPERATION_FILE_DIR_READ = 7;//操作数是文件，但是需要有文件夹的权限
    public static final int OPERATION_FILE_DIR_WRITE = 8;
    public static final int OPERATION_DIRECTORY_CHANGE = 9;
    public static final int OPERATION_DIRECTORY_LIST = 10;

    LinkedList<Pair<Path, Integer>> mDirs = new LinkedList<Pair<Path, Integer>>();
    LinkedList<Pair<Path, Integer>> mFiles = new LinkedList<Pair<Path, Integer>>();

    public void addPermission(boolean isDir, boolean canRead, boolean canWrite, boolean canExecute, Path path, char suffix) {
        Pair<Path, Integer> access = new Pair<Path, Integer>();
        access.setValue1(path);
        int accessValue = 0;
        if (canRead) {
            accessValue |= ACCESS_READ;
        }
        if (canWrite) {
            accessValue |= ACCESS_WRITE;
        }
        if (canExecute) {
            accessValue |= ACCESS_EXECUTE;
        }
        switch (suffix) {
            case '*':
                accessValue |= ACCESS_INHERIT;
            case '?':
                accessValue |= ACCESS_DIRECTORY;
                break;
            case '-':
            default:
        }
        access.setValue2(accessValue);
        if (isDir) {
            mDirs.add(access);
        } else {
            mFiles.add(access);
        }
    }

    public boolean checkAccess(Path path, int operation) {
        int code;
        switch (operation) {
            case OPERATION_FILE_DIR_WRITE://这几个操作都是需要文件夹拥有写的权限
            case OPERATION_FILE_CREATE:
            case OPERATION_FILE_DELETE:
            case OPERATION_FILE_RENAME_FROM:
            case OPERATION_FILE_RENAME_TO:
                //先取父目录
                path = path.link("../");
                code = getAccess(path, true);
                return (code & ACCESS_WRITE) != 0;

            case OPERATION_FILE_DIR_READ: //需要文件夹的读权限
                //先取父目录
                path = path.link("../");
            case OPERATION_DIRECTORY_LIST:
                code = getAccess(path, true);
                return (code & ACCESS_READ) != 0;

            case OPERATION_DIRECTORY_CHANGE: //需要文件夹的执行权限
                code = getAccess(path, true);
                return (code & ACCESS_EXECUTE) != 0;

            case OPERATION_FILE_WRITE: //需要文件的写权限
                code = getAccess(path, false);
                return (code & ACCESS_WRITE) != 0;

            case OPERATION_FILE_READ: //需要文件的读权限
                code = getAccess(path, false);
                return (code & ACCESS_READ) != 0;
        }
        return false;
    }

    public int getAccess(Path path, boolean isDir) {
        LinkedList<Pair<Path, Integer>> accessDefs = isDir ? mDirs : mFiles;

        for (int i = accessDefs.size() - 1; i >= 0; i--) {
            Pair<Path, Integer> p = accessDefs.get(i);
            Path pPath = p.getValue1();
            int pCode = p.getValue2();
            if ((pCode & ACCESS_DIRECTORY) != 0) {
                if (path.isChildPath(pPath)) {
                    if ((pCode & ACCESS_INHERIT) != 0) {
                        if (path.levels() > pPath.levels()) {
                            return pCode;
                        }
                    } else {
                        if (path.levels() - 1 == pPath.levels()) {
                            return pCode;
                        }
                    }
                }
            } else {
                if (pPath.equals(path)) {
                    return pCode;
                }
            }
        }

        return 0;
    }

    public int getAccess(Path path) {
        return getAccess(path, path.getFile().isDirectory());
    }

}
