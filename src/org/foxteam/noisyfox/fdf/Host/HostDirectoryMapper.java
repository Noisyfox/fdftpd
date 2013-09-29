package org.foxteam.noisyfox.fdf.Host;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-28
 * Time: 上午12:13
 * To change this template use File | Settings | File Templates.
 */
public class HostDirectoryMapper {

    private final Object syncObj = new Object();

    public Editor edit(int node) {
        return new Editor(node);
    }

    /**
     * 文件夹映射，返回指定的目录所在的节点编号。
     * 如果该路径不指向任何节点，则返回-1（通常表明该路径存在于本地）
     * @param path 需要映射的文件路径
     * @return 节点编号，或-1
     */
    public int map(String path){
        return -1;
    }

    public class Editor {
        private final int mNode;
        private boolean isCommited = false;
        private ArrayList<String> mPaths = new ArrayList<String>();

        private Editor(int node) {
            mNode = node;
        }

        public synchronized boolean commit() {
            synchronized (syncObj) {
                if (isCommited) {
                    throw new IllegalStateException("Editor already committed or given up!");
                }
                isCommited = true;

                for(String p : mPaths){

                }
                return false;
            }
        }

        public synchronized void giveup() {
            if (isCommited) {
                throw new IllegalStateException("Editor already committed or given up!");
            }
            isCommited = true;
        }

        public synchronized void add(String dummyPath) {
            if (isCommited) {
                throw new IllegalStateException("Editor already committed or given up!");
            }
            mPaths.add(dummyPath);
        }
    }
}
