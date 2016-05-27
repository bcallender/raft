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
        parent.sendHeartbeats();
    }
}
