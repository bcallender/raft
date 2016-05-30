/**
 * Created by brandon on 5/25/16.
 */
public class HeartbeatSender implements Runnable {

    private Node parent;

    public HeartbeatSender(Node parent) {
        this.parent = parent;
    }

    @Override
    public void run() {
        try {
            parent.sendHeartbeats();
        } catch (Exception e) { //catch any exception that happens in the thread so we can see them on the logger.
            Logger.error(String.format("%s, %s ", parent.getNodeName(), e.toString()));
        }

    }
}
