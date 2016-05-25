/**
 * Created by brandon on 5/24/16.
 */
public class RPCMessageResponse {
    int term;
    boolean success;

    public RPCMessageResponse(int term, boolean success) {
        this.term = term;
        this.success = success;
    }
}
