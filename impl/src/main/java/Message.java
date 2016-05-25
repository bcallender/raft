import com.google.gson.Gson;

import java.io.Serializable;
import java.nio.charset.Charset;

/**
 * Abstract message class for attributes that will be a part of any message (type, destination, id, source).
 */
public abstract class Message implements Serializable {

    MessageType type;
    String destination;
    int id;
    String source;

    public Message(MessageType type, String destination, int id, String source) {
        this.type = type;
        this.destination = destination;
        this.id = id;
        this.source = source;

    }

    //all message types can be serialized into JSON
    public byte[] serialize(Gson gson) {
        return gson.toJson(this).getBytes(Charset.defaultCharset());
    }
}
