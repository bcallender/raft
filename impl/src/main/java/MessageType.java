import com.google.gson.annotations.SerializedName;

/**
 * Enum for all the different messageTypes we send so we can switch on the enum and handle each type.
 */
public enum MessageType {
    /* This is very messy, but unfortunately necessary. We wanted to adhere to standard enum rules with respect to case
    * which helps readability. Unfortunately this makes it hard to parse the chistributed messages, so we had to add the
     * lower case strings in the constructor to get around this. GSON ignores these when it serializes the enums, so
     * we also have to specify the @SerializedName for gson to make the JSON messages. */
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
    @SerializedName("setRequestForward")
    SET_REQUEST_FORWARD("setRequestForward"),
    @SerializedName("getRequestForward")
    GET_REQUEST_FORWARD("getRequestForward");




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
