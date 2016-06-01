import com.google.gson.Gson;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Structure of an appendEntriesMessage sent as a heartbeat from a leader to its followers. Instance variables are as
 * described in the RAFT paper. Subtype of Message Class to get its standard fields (type, destination, source ,id).
 * Like all subclasses, implements a static deserialize function to translate byte[] payloads into instances of the class.
 */
public class AppendEntriesMessage extends Message implements Serializable {
    private int term;
    private String leaderId;
    private int prevLogIndex;
    private int prevLogTerm;
    private List<Entry> entries;
    private int leaderCommit;

    public AppendEntriesMessage(MessageType type, String destination, int id, String source, int term, String leaderId,
                                int prevLogIndex, int prevLogTerm, List<Entry> entries, int leaderCommit) {
        super(type, destination, id, source);
        this.term = term;
        this.leaderId = leaderId;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.entries = entries;
        this.leaderCommit = leaderCommit;
    }

    public static AppendEntriesMessage deserialize(byte[] payload, Gson gson) {
        String jsonPayload = new String(payload, Charset.defaultCharset());
        return gson.fromJson(jsonPayload, AppendEntriesMessage.class);
    }

    public int getPrevLogTerm() {
        return prevLogTerm;
    }

    public int getPrevLogIndex() {
        return prevLogIndex;
    }

    public String getLeaderId() {
        return leaderId;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public int getLeaderCommit() {
        return leaderCommit;
    }

    public int getTerm() {

        return term;
    }
}
