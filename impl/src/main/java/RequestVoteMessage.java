import com.google.gson.Gson;

import java.io.Serializable;
import java.nio.charset.Charset;

/**
 * Structure of an requestVoteMessage sent when a node times out and requests an election. Instance variables are as
 * described in the RAFT paper. Subtype of Message Class to get its standard fields (type, destination, source ,id).
 * Like all subclasses, implements a static deserialize function to translate byte[] payloads into instances of the class.
 */
public class RequestVoteMessage extends Message implements Serializable {

    private int term;
    private String candidateId;
    private int lastLogIndex;
    private int lastLogTerm;

    public RequestVoteMessage(MessageType type, String destination, int id, String source, int term, String candidateId,
                              int lastLogIndex, int lastLogTerm) {
        super(type, destination, id, source);
        this.term = term;
        this.candidateId = candidateId;
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
    }

    public static RequestVoteMessage deserialize(byte[] payload, Gson gson) {
        String jsonPayload = new String(payload, Charset.defaultCharset());
        return gson.fromJson(jsonPayload, RequestVoteMessage.class);
    }

    public int getTerm() {
        return term;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public int getLastLogIndex() {
        return lastLogIndex;
    }

    public int getLastLogTerm() {
        return lastLogTerm;
    }
}
