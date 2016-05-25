import com.google.gson.Gson;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Created by brandon on 5/24/16.
 */
public class RequestVoteMessage extends Message implements Serializable {

    private int term;
    private int candidateId;
    private int lastLogIndex;
    private int lastLogTerm;

    public RequestVoteMessage(MessageType type, List<String> destination, int id, String source, int term, int candidateId, int lastLogIndex, int lastLogTerm) {
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
}
