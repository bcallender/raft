import com.google.gson.Gson;

import java.nio.charset.Charset;

/**
 * Created by brandon on 5/26/16.
 */
public class ErrorMessage extends Message {

    private String error;

    public ErrorMessage(MessageType type, String destination, int id, String source, String error) {
        super(type, destination, id, source);
        this.error = error;
    }

    public static ErrorMessage deserialize(byte[] payload, Gson gson) {
        String jsonPayload = new String(payload, Charset.defaultCharset());
        return gson.fromJson(jsonPayload, ErrorMessage.class);
    }

    public static ErrorMessage deserialize(String payload, Gson gson) {
        return gson.fromJson(payload, ErrorMessage.class);

    }

    public String getError() {
        return error;
    }
}
