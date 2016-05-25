import com.google.gson.Gson;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Created by brandon on 5/24/16.
 */
public class RPCMessageResponse extends Message implements Serializable {
    int term;
    boolean success;

    public RPCMessageResponse(MessageType type, List<String> destination, int id, String source, int term, boolean success) {
        super(type, destination, id, source);
        this.term = term;
        this.success = success;
    }

    public static RPCMessageResponse deserialize(byte[] payload, Gson gson) {
        String jsonPayload = new String(payload, Charset.defaultCharset());
        return gson.fromJson(jsonPayload, RPCMessageResponse.class);
    }

}
