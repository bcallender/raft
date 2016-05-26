import com.google.gson.annotations.SerializedName;

/**
 * Created by brandon on 5/22/16.
 */
public enum MessageType {
    GET("get"), SET("set"), DUPL("dupl"), HELLO("hello"), HELLO_RESPONSE("helloResponse"),
    UNKNOWN("UNKNOWN"), SET_RESPONSE("setResponse"), GET_RESPONSE("getResponse"),
    @SerializedName("requestVote")
    REQUEST_VOTE("requestVote"),
    @SerializedName("appendEntries")
    APPEND_ENTRIES("appendEntries"),
    @SerializedName("requestVoteResponse")
    REQUEST_VOTE_RESPONSE("requestVoteResponse"),
    @SerializedName("appendEntriesResponse")
    APPEND_ENTRIES_RESPONSE("appendEntriesResponse");



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
