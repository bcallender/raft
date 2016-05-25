import java.util.ArrayList;
import java.util.List;

public class AppendEntriesMessageBuilder {
    private final MessageType type = MessageType.APPEND_ENTRIES;
    private String destination;
    private int id;
    private String source;
    private int term;
    private String leaderId;
    private int prevLogIndex;
    private int prevLogTerm;
    private List<Entry> entries = new ArrayList<>();
    private int leaderCommit;

    public AppendEntriesMessageBuilder setDestination(String destination) {
        this.destination = destination;
        return this;
    }

    public AppendEntriesMessageBuilder setId(int id) {
        this.id = id;
        return this;
    }

    public AppendEntriesMessageBuilder setSource(String source) {
        this.source = source;
        return this;
    }

    public AppendEntriesMessageBuilder setTerm(int term) {
        this.term = term;
        return this;
    }

    public AppendEntriesMessageBuilder setLeaderId(String leaderId) {
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

    public AppendEntriesMessageBuilder addEntry(Entry entry) {
        this.entries.add(entry);
        return this;
    }

    public AppendEntriesMessageBuilder setLeaderCommit(int leaderCommit) {
        this.leaderCommit = leaderCommit;
        return this;
    }

    public AppendEntriesMessage createAppendEntriesMessage() {
        return new AppendEntriesMessage(type, destination, id, source, term, leaderId, prevLogIndex, prevLogTerm, entries, leaderCommit);
    }
}