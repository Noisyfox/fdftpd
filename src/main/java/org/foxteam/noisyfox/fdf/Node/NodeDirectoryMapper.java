package org.foxteam.noisyfox.fdf.Node;

import org.foxteam.noisyfox.fdf.Pair;
import org.foxteam.noisyfox.fdf.Path;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-28
 * Time: 上午12:19
 * To change this template use File | Settings | File Templates.
 */
public class NodeDirectoryMapper {
    private final LinkedList<Pair<Path, Path>> mPathPairs = new LinkedList<Pair<Path, Path>>();
    private final PathNode mRootNode = new PathNode();

    public void addPathMap(String nodePath, String dummyPath) {
        Path pNode = Path.valueOf(nodePath);
        Path pDummy = Path.valueOf(dummyPath);
        if (!pNode.isFullPath() || !pDummy.isFullPath()
                || pNode.getRelativity() != Path.RELA_ROOT
                || pDummy.getRelativity() != Path.RELA_ROOT) {
            System.out.printf("Illegal map path:\"" + nodePath + "\"->\"" + dummyPath + "\"");
            return;
        }
        Pair<Path, Path> pathPair = new Pair<Path, Path>(pNode, pDummy);
        mPathPairs.add(pathPair);

        PathNode node = mRootNode;
        String[] levels = pDummy.toArray();
        for (String s : levels) {
            PathNode _tmpNode = node.nextLevel.get(s);
            if (_tmpNode == null) {
                _tmpNode = new PathNode();
                _tmpNode.dirName = s;
                node.nextLevel.put(s, _tmpNode);
            }
            node = _tmpNode;
        }
        node.dummyPath = pDummy;
        node.mappedPath = pNode;
    }

    public LinkedList<Pair<Path, Path>> getAllPathPairs() {
        return mPathPairs;
    }

    public Path map(Path dummyPath) {
        String[] pathLevels = dummyPath.toArray();
        PathNode node = mRootNode;
        Path rp = null;
        Path dp = null;
        for (String s : pathLevels) {
            PathNode _tmpNode = node.nextLevel.get(s);
            if (_tmpNode != null) {
                node = _tmpNode;
                if (node.mappedPath != null) {
                    dp = node.dummyPath;
                    rp = node.mappedPath;
                }
            } else {
                break;
            }
        }

        if (rp == null) {
            return null;
        } else {
            return rp.link(dummyPath.getRelativePath(dp));
        }
    }

    private class PathNode {
        String dirName;
        Path mappedPath = null;
        Path dummyPath = null;
        HashMap<String, PathNode> nextLevel = new HashMap<String, PathNode>();
    }
}
