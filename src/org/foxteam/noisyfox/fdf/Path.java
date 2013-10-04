package org.foxteam.noisyfox.fdf;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-29
 * Time: 上午12:52
 * 一个用统一标准储存和操作路径的类，目的在于统一 unix 和 windows 下不同的路径表示方法
 * "../"代表在当前路径基础上向上一层，"./"代表当前路径
 * "/"为根目录，任何目录都的绝对路径都是以此为开头。
 * 在windows下，一个路径也是会以"/"作为开头，在盘符之前。
 */
public class Path {
    private static final HashMap<String, Path> mMappedPath = new HashMap<String, Path>();

    static {
        Path p = new Path(".");
        mMappedPath.put(".", p);
        mMappedPath.put("./", p);
        mMappedPath.put(".\\", p);
        mMappedPath.put("", p);
        p = new Path("~");
        mMappedPath.put("~", p);
        mMappedPath.put("~/", p);
        mMappedPath.put("~\\", p);
        p = new Path("/");
        mMappedPath.put("/", p);
        mMappedPath.put("\\", p);
    }


    //路径的相对性
    public static final int RELA_ROOT = 1;//以根目录起始
    public static final int RELA_HOME = 2;//以home起始
    public static final int RELA_CURR = 3;//以当前目录起始

    private final String[] mLevels;//储存路径的层次结构，第一个为 "/""~""."三者之一，分别表示根目录，home及当前目录
    private final File mFile;
    private int startCount = 0;//记录该路径开头有多少个连续的"../"
    private int endCount = 0;//记录该路径结尾处有多少个连续的非"../"

    private Path(String[] levels, int start, int end) {
        mLevels = levels;
        startCount = start;
        endCount = end;
        mFile = new File(getAbsolutePath());
    }

    public Path() {
        this(Path.class.getClassLoader().getResource("/").getPath());
    }

    public Path(String path) {
        path = path.replace("\\", "/").trim();//将反斜杠替换为斜杠
        while (path.contains("//")) {
            path = path.replace("//", "/");//将所有的多个相连的斜杠转换为单个
        }
        String[] levels = path.split("/");
        if (levels == null || levels.length == 0) {//空路径，即为根目录
            mLevels = new String[]{"/"};
            mFile = new File("/");
            return;
        }
        //合并输入路径中的".."和"."
        int point = -1, first = -1;
        for (int i = 0; i < levels.length; i++) {
            if (!levels[i].isEmpty() && !".".equals(levels[i])) {
                if ("..".equals(levels[i])) {
                    if (first != -1) {
                        endCount--;
                        point--;
                        if (point < first) first = -1;
                    } else {
                        startCount++;
                        endCount = 0;
                        point++;
                        levels[point] = levels[i];
                    }
                } else {
                    endCount++;
                    point++;
                    levels[point] = levels[i];
                    if (first == -1) first = point;
                }
            }
        }
        if (point < 0) {
            mLevels = new String[]{"/"};
            mFile = new File("/");
            return;
        }

        mLevels = new String[point + 2];
        //判断第一位是根目录、当前目录或home
        if (path.startsWith("~")) {
            mLevels[0] = "~";
        } else if (path.startsWith("/")) {
            mLevels[0] = "/";
        } else {
            //判断windows路径
            if (levels[0].endsWith(":")) {//驱动器号
                mLevels[0] = "/";
            } else {
                mLevels[0] = ".";
            }
        }
        System.arraycopy(levels, 0, mLevels, 1, mLevels.length - 1);

        mFile = new File(getAbsolutePath());
    }

    public Path link(String path) {
        return link(valueOf(path));
    }

    public Path link(Path path) {
        /*
        int firstNoneEmptyCount = 0;
        for(int i = mLevels.length - 1; i > 0; i--){
            if("..".equals(mLevels[i]))break;
            else firstNoneEmptyCount++;

            if(firstNoneEmptyCount >= path.mLevels.length || ! "..".equals(path.mLevels[firstNoneEmptyCount])){
                break;
            }
        }
        int newLen = mLevels.length + path.mLevels.length - firstNoneEmptyCount * 2 + 1;
        String[] levels = new String[newLen];
        for(int i = 0; i <= mLevels.length - firstNoneEmptyCount; i++){
            levels[i] = mLevels[i];
        }
        for(int i = firstNoneEmptyCount; i < path.mLevels.length; i++){
            int index = mLevels.length - firstNoneEmptyCount * 2 + i + 1;
            levels[index] = path.mLevels[i];
        }
        */

        int maxReduce = Math.min(endCount, path.startCount);
        String[] levels = new String[mLevels.length + path.mLevels.length - 1 - maxReduce * 2];
        System.arraycopy(mLevels, 0, levels, 0, mLevels.length - maxReduce);
        for (int i = maxReduce + 1; i < path.mLevels.length; i++) {
            int index = mLevels.length - maxReduce * 2 + i - 1;
            levels[index] = path.mLevels[i];
        }

        int s = startCount;
        if (endCount <= path.startCount) {//前一个的结尾被消完
            s += path.startCount - endCount;
        }


        return new Path(levels, s, levels.length - s - 1);
    }

    public String getAbsolutePath() {
        StringBuilder sb = new StringBuilder();
        if (mLevels.length == 1) {
            sb.append(mLevels[0]);
        } else {
            if (!"/".equals(mLevels[0])) {
                sb.append(mLevels[0]);
            }
            for (int i = 1; i < mLevels.length; i++) {
                sb.append("/");
                sb.append(mLevels[i]);
            }
        }
        return sb.toString();
    }

    public String getRelativePath(String reference) {
        return getRelativePath(valueOf(reference));
    }

    /**
     * 获取相对路径，注意只有完整路径才能获取相对路径
     *
     * @param reference 相对路径的参照路径，也必须是完整路径，且必须是该路径的父级
     * @return 相对于reference的路径或null如果不符合输入条件
     */
    public String getRelativePath(Path reference) {
        if (isFullPath() && reference.isFullPath() && isChildPath(reference)) {
            if (mLevels.length == reference.mLevels.length) {
                return "./";
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append(".");
                for (int i = reference.mLevels.length; i < mLevels.length; i++) {
                    sb.append("/");
                    sb.append(mLevels[i]);
                }
                return sb.toString();
            }
        }
        return null;
    }

    public int levels() {
        return mLevels.length - 1;
    }

    public boolean isFullPath() {
        return startCount == 0 && endCount == mLevels.length - 1;
    }

    /**
     * 判断该路径是不是指定路径的子路径
     *
     * @param path 父目录
     * @return 是否是指定路径的子路径
     */
    public boolean isChildPath(String path) {
        return isChildPath(Path.valueOf(path));
    }

    /**
     * 判断该路径是不是指定路径的子路径
     *
     * @param path 父目录
     * @return 是否是指定路径的子路径
     */
    public boolean isChildPath(Path path) {
        if (mLevels.length >= path.mLevels.length) {
            for (int i = 0; i < path.mLevels.length; i++) {
                if (!mLevels[i].equals(path.mLevels[i])) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public File getFile() {
        return mFile;
    }

    /**
     * 获取路径的相对性
     *
     * @return
     */
    public int getRelativity() {
        if (".".equals(mLevels[0])) {
            return RELA_CURR;
        } else if ("~".equals(mLevels[0])) {
            return RELA_HOME;
        } else {
            return RELA_ROOT;
        }
    }

    public String[] toArray() {
        return Arrays.copyOf(mLevels, mLevels.length);
    }

    @Override
    public String toString() {
        return getAbsolutePath();
    }

    @Override
    public boolean equals(Object obj) {
        if (Path.class.isInstance(obj)) {
            return toString().equals(obj.toString());
        }
        return false;
    }

    public static Path valueOf(String path) {
        Path p = mMappedPath.get(path);
        return p == null ? new Path(path) : p;
    }

    public static Path valueOf(File f) {
        return valueOf(f.getAbsolutePath());
    }

}
