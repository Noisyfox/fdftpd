package org.foxteam.noisyfox.fdf.Host;

/**
 * Created with IntelliJ IDEA.
 * User: Noisyfox
 * Date: 13-10-7
 * Time: 下午9:43
 * To change this template use File | Settings | File Templates.
 */
public class HostNodeController {
    private final HostSession mHostSession;
    private final Host mHost;
    private final HostServant mHostServant;
    private int mSelectedNode = -1;

    public HostNodeController(HostServant hostServant) {
        mHostServant = hostServant;
        mHost = hostServant.mHost;
        mHostSession = hostServant.mSession;
    }

    public void chooseNode(int node) {
        if (node < 0 || node >= mHostSession.userNodeSession.length) {
            throw new IndexOutOfBoundsException();
        }

        mSelectedNode = node;
    }

    private void prepareSelectedNode() {
        if (mSelectedNode == -1) {
            throw new IllegalStateException("No node has been chosen!");
        }

        HostNodeSession[] sessions = mHostSession.userNodeSession;

        if (sessions[mSelectedNode] == null || !sessions[mSelectedNode].isSessionAlive()) {
            sessions[mSelectedNode] = mHost.getHostNodeConnector(mSelectedNode).getNodeSession(mHostServant);
        }
    }

    public HostNodeSession getNodeSession() {
        prepareSelectedNode();
        return mHostSession.userNodeSession[mSelectedNode];
    }

    public void killAll() {
        for (HostNodeSession session : mHostSession.userNodeSession) {
            if (session != null) {
                session.kill();
            }
        }
    }
}
