/**
 * Created by brandon on 5/26/16.
 */
public class ClientCommand {
    private MessageType type;
    private String key;

    public ClientCommand(MessageType type, String key, String value) {
        this.type = type;
        this.key = key;
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

}

