import com.google.gson.annotations.SerializedName;

/**
 * Created by brandon on 5/22/16.
 */
public enum MessageType {
    @SerializedName("get")
    GET("get"),
    @SerializedName("set")
    SET("set"),
    @SerializedName("hello")
    HELLO("hello"),
    @SerializedName("helloResponse")
    HELLO_RESPONSE("helloResponse"),
    UNKNOWN("UNKNOWN"),
    @SerializedName("setResponse")
    SET_RESPONSE("setResponse"),
    @SerializedName("getResponse")
    GET_RESPONSE("getResponse"),
    @SerializedName("requestVote")
    REQUEST_VOTE("requestVote"),
    @SerializedName("appendEntries")
    APPEND_ENTRIES("appendEntries"),
    @SerializedName("requestVoteResponse")
    REQUEST_VOTE_RESPONSE("requestVoteResponse"),
    @SerializedName("appendEntriesResponse")
    APPEND_ENTRIES_RESPONSE("appendEntriesResponse"),
    @SerializedName("requestForward")
    REQUEST_FORWARD("requestForward"),
    @SerializedName("requestForwardResponse")
    REQUEST_FORWARD_RESPONSE("requestForwardResponse");




    private final String representation;

    MessageType(String repr) {
        this.representation = repr;
    }

    // given a string type from JSON, return the MessageType enum corresponding to it.
    public static MessageType parse(String s) {
        MessageType m;
        try {
            m = valueOf(s.trim().toUpperCase()); //optimistic matching
        } catch (IllegalArgumentException e) {
            for (MessageType mt : MessageType.values()) { //pessimistic matching with custom values.
                if (mt.representation.equals(s)) {
                    return mt;
                }
            }
            return UNKNOWN;
        }
        return m;

    }

    @Override
    public String toString() {
        return this.representation;
    }
}
