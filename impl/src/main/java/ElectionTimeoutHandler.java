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
        try {
            parent.transitionTo(Node.Role.CANDIDATE);
        } catch (Exception e) { //catch any exception that happens in the thread so we can see them on the logger.
            Logger.error(String.format("%s, %s ", parent.getNodeName(), e.toString()));
        }
    }
}
