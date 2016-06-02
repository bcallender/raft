import org.zeromq.ZMQ;

import java.nio.channels.ClosedByInterruptException;

/**
 * Should control the sending of heartbeats on a new thread.
 */
public class HeartbeatSender implements Runnable {

    private Node parent;
    private BrokerManager brokerManager;

    public HeartbeatSender(Node parent, BrokerManager brokerManager) {
        this.parent = parent;
        this.brokerManager = brokerManager;
    }

    @Override
    public void run() {
        ZMQ.Socket threadSocket = brokerManager.getHeartBeatSock();

        try {
            parent.sendHeartbeats(threadSocket);
        } catch (ClosedByInterruptException ce) {
            Logger.warning(String.format("%s, heartbeat thread was killed off unexpectedly. Closing socket...",
                    parent.getNodeName()));
        } catch (Exception e) { //catch any exception that happens in the thread so we can see them on the logger.
            Logger.error(String.format("%s, %s %s", parent.getNodeName(), e.getMessage(), e.toString()));
            e.printStackTrace();
        }
        threadSocket.close();

    }
}
