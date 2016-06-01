import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.Serializable;
import java.nio.charset.Charset;

/**
 * General message class for attributes that will be a part of any message (type, destination, id, source).
 * This is a superclass of all specific message classes, so it implements serialize, which is the same across all the
 * MessageTypes. serializeToObject is a helper method used to generate an intermediate JsonObject to add custom fields to it.
 * We thought this was a good middle ground instead of creating a custom message type for all the different messageTypes.
 */
public class Message implements Serializable {

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

    public JsonObject serializeToObject(Gson gson) {
        return gson.toJsonTree(this).getAsJsonObject();
    }
}
