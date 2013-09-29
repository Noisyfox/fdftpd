package org.foxteam.noisyfox.fdf.Node;

import org.foxteam.noisyfox.fdf.Path;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-9-28
 * Time: 上午12:19
 * To change this template use File | Settings | File Templates.
 */
public class NodeDirectoryMapper {
    Path dirFrom, dirTo;

    public NodeDirectoryMapper(String from, String to)throws IOException {
        dirFrom = Path.valueOf(from);
        dirTo = Path.valueOf(to);
    }
}
