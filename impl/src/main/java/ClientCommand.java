import java.util.HashSet;
import java.util.Set;

/**
 * This class represents a command from a client that is currently being processed by a node. Inside the node, these reside
 * in a hashMap that tracks the current commands in flight, indexed by their requestId. This helps to make sure that clients always
 * respond to or forward request correctly.
 */
public class ClientCommand {
    private MessageType type;
    private String key;
    private String value;
    private Set<String> responses;
    /* in the case of a GET command, keeps track of the number of affirmative
    AppendEntriesResponses we've gotten so far from our heartbeats. If more than a quorum of nodes still think we are the
    leader, we can respond to the GET request. */


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

