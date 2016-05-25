/**
 * Created by brandon on 5/22/16.
 */
public enum MessageType {
    GET("get"), SET("set"), DUPL("dupl"), HELLO("hello"), HELLO_RESPONSE("helloResponse"),
    UNKNOWN("UNKNOWN"), SET_RESPONSE("setResponse"), GET_RESPONSE("getResponse");


    private final String representation;

    MessageType(String repr) {
        this.representation = repr;
    }

    public static MessageType safeValueOf(String s) {
        MessageType m;
        try {
            m = valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            m = UNKNOWN;
        }
        return m;

    }

    @Override
    public String toString() {
        return this.representation;
    }
}
