import com.google.gson.Gson;

import java.io.Serializable;
import java.nio.charset.Charset;
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

    public AppendEntriesMessage(MessageType type, List<String> destination, int id, String source, int term, int leaderId, int prevLogIndex, int prevLogTerm, List<Entry> entries, int leaderCommit) {
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
}
