package org.foxteam.noisyfox.fdf.Host;

import org.foxteam.noisyfox.fdf.Path;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-28
 * Time: 上午12:13
 * To change this template use File | Settings | File Templates.
 */
public class HostDirectoryMapper {

    private final Object syncObj = new Object();

    private final PathNode mRootNode = new PathNode();

    public Editor edit(int node) {
        return new Editor(node);
    }

    /**
     * 文件夹映射，返回指定的目录所在的节点编号。
     * 如果该路径不指向任何节点，则返回-1（通常表明该路径存在于本地）
     *
     * @param path 需要映射的文件路径
     * @return 节点编号，或-1
     */
    public int map(String path) {
        Path p = Path.valueOf(path);
        String[] pathLevels = p.toArray();
        PathNode node = mRootNode;
        for (String s : pathLevels) {
            PathNode _tmpNode = node.nextLevel.get(s);
            if (_tmpNode != null) {
                node = _tmpNode;
            } else {
                break;
            }
        }

        return node.nodeNumber;
    }

    private class PathNode {
        String dirName;
        int nodeNumber = -1;
        HashMap<String, PathNode> nextLevel = new HashMap<String, PathNode>();
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
                Path[] paths = new Path[mPaths.size()];
                for (int i = 0; i < paths.length; i++) {
                    paths[i] = Path.valueOf(mPaths.get(i));
                    if (!paths[i].isFullPath() || !paths[i].isChildPath(Path.valueOf("/"))) {
                        return false;
                    }
                }

                for (Path p : paths) {
                    PathNode node = mRootNode;
                    String[] levels = p.toArray();
                    for (String s : levels) {
                        PathNode _tmpNode = node.nextLevel.get(s);
                        if (_tmpNode == null) {
                            _tmpNode = new PathNode();
                            _tmpNode.dirName = s;
                            node.nextLevel.put(s, _tmpNode);
                        }
                        node = _tmpNode;
                    }
                    node.nodeNumber = mNode;
                }
                return true;
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
