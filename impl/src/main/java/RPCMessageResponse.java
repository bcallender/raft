import com.google.gson.Gson;

import java.io.Serializable;
import java.nio.charset.Charset;

/**
 * Standard response to either an appendEntries or a requestVote -- both contain the same fields, success, term.
 */
public class RPCMessageResponse extends Message implements Serializable {
    int term;
    boolean success;
    int logIndex; //logIndex used to reply to appendEntries as an optimzation to the raft algorithm -- makes calculating
    //matchIndex much easier.
    
    public RPCMessageResponse(MessageType type, String destination, int id, String source, int term, boolean success, int logIndex) {
        super(type, destination, id, source);
        this.term = term;
        this.success = success;
        this.logIndex = logIndex;
    }

    public static RPCMessageResponse deserialize(byte[] payload, Gson gson) {
        String jsonPayload = new String(payload, Charset.defaultCharset());
        return gson.fromJson(jsonPayload, RPCMessageResponse.class);
    }

}
