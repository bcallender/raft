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
        int currentTerm = parent.getCurrentTerm() + 1;
        parent.setCurrentTerm(currentTerm);
        Entry lastLog = parent.getLastLog();
//        if (lastLog == null)
//            lastLog = new Entry();

        RequestVoteMessage rvm = new RequestVoteMessageBuilder()
                .setTerm(currentTerm)
                .setCandidateId(parent.getNodeName())
                .setLastLogIndex(0)
                .setLastLogTerm(0)
                .createRequestVoteMessage();
    }
}
