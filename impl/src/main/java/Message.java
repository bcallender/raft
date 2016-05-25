import com.google.gson.Gson;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Created by brandon on 5/24/16.
 */
public abstract class Message implements Serializable {

    MessageType type;
    List<String> destination;
    int id;
    String source;

    public Message(MessageType type, List<String> destination, int id, String source) {
        this.type = type;
        this.destination = destination;
        this.id = id;
        this.source = source;

    }

    public byte[] serialize(Gson gson) {
        return gson.toJson(this).getBytes(Charset.defaultCharset());
    }
}
