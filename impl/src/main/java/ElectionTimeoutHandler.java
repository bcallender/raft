/**
 * Should control the progression of a FOLLOWER to a CANDIDATE following an election timeout.
 */
public class ElectionTimeoutHandler implements Runnable {

    private Node parent;

    public ElectionTimeoutHandler(Node parent) {
        this.parent = parent;
    }

    @Override
    public void run() {
        parent.transitionTo(Node.Role.CANDIDATE);
    }
}
