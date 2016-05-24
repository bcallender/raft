/**
 * Created by brandon on 5/22/16.
 */
public enum MessageType {
    GET, SET, DUPL, HELLO, UNKNOWN;

    public static MessageType safeValueOf(String s) {
        MessageType m;
        try {
            m = valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            m = UNKNOWN;
        }
        return m;

    }
}
