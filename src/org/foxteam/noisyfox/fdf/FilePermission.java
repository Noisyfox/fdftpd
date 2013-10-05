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
    public static final int ACCESS_DIRECTORY = 0x4;
    public static final int ACCESS_INHERIT = 0x8;

    LinkedList<Pair<Path, Integer>> mDirs = new LinkedList<Pair<Path, Integer>>();
    LinkedList<Pair<Path, Integer>> mFiles = new LinkedList<Pair<Path, Integer>>();

    public void addPermission(boolean isDir, boolean canRead, boolean canWrite, Path path, char suffix) {
        Pair<Path, Integer> access = new Pair<Path, Integer>();
        access.setValue1(path);
        int accessValue = 0;
        if (canRead) {
            accessValue |= ACCESS_READ;
        }
        if (canWrite) {
            accessValue |= ACCESS_WRITE;
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

    public boolean checkAccess(Path path) {
        return false;
    }

}
