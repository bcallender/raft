import java.io.Serializable;
import java.util.List;

/**
 * Created by brandon on 5/24/16.
 */
public class AppendEntriesMessage extends Message implements Serializable {
    int term;
    int leaderId;
    int prevLogIndex;
    int prevLogTerm;
    List<Entry> entries;
    int leaderCommit;

    public AppendEntriesMessage(int term, int leaderId, int prevLogIndex, int prevLogTerm, List<Entry> entries, int leaderCommit) {
        this.term = term;
        this.leaderId = leaderId;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.entries = entries;
        this.leaderCommit = leaderCommit;
    }


}
