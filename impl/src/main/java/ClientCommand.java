import java.util.HashSet;
import java.util.Set;

/**
 * Created by brandon on 5/26/16.
 */
public class ClientCommand {
    private MessageType type;
    private String key;
    private String value;
    private Set<String> responses;


    public ClientCommand(MessageType type, String key, String value) {
        this.type = type;
        this.key = key;
        this.value = value;
        this.responses = new HashSet<>();
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void addResponse(String peer) {
        this.responses.add(peer);
    }

    public int getResponsesSize() {
        return this.responses.size();
    }
}

