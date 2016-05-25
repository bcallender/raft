import java.util.List;

public class AppendEntriesMessageBuilder {
    private int term;
    private int leaderId;
    private int prevLogIndex;
    private int prevLogTerm;
    private List<Entry> entries;
    private int leaderCommit;

    public AppendEntriesMessageBuilder setTerm(int term) {
        this.term = term;
        return this;
    }

    public AppendEntriesMessageBuilder setLeaderId(int leaderId) {
        this.leaderId = leaderId;
        return this;
    }

    public AppendEntriesMessageBuilder setPrevLogIndex(int prevLogIndex) {
        this.prevLogIndex = prevLogIndex;
        return this;
    }

    public AppendEntriesMessageBuilder setPrevLogTerm(int prevLogTerm) {
        this.prevLogTerm = prevLogTerm;
        return this;
    }

    public AppendEntriesMessageBuilder setEntries(List<Entry> entries) {
        this.entries = entries;
        return this;
    }

    public AppendEntriesMessageBuilder setLeaderCommit(int leaderCommit) {
        this.leaderCommit = leaderCommit;
        return this;
    }

    public AppendEntriesMessage createAppendEntriesMessage() {
        return new AppendEntriesMessage(term, leaderId, prevLogIndex, prevLogTerm, entries, leaderCommit);
    }
}