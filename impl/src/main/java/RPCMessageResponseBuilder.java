/* We thought that the message had a huge constructor, so intellij wrote a builder for us.
* */

public class RPCMessageResponseBuilder {
    private MessageType type;
    private String destination;
    private int id;
    private String source;
    private int term;
    private boolean success;
    private int logIndex;

    public void setLogIndex(int logIndex) {
        this.logIndex = logIndex;
    }

    public RPCMessageResponseBuilder setType(MessageType type) {
        this.type = type;
        return this;
    }

    public RPCMessageResponseBuilder setDestination(String destination) {
        this.destination = destination;
        return this;
    }

    public RPCMessageResponseBuilder setId(int id) {
        this.id = id;
        return this;
    }

    public RPCMessageResponseBuilder setSource(String source) {
        this.source = source;
        return this;
    }

    public RPCMessageResponseBuilder setTerm(int term) {
        this.term = term;
        return this;
    }

    public RPCMessageResponseBuilder setSuccess(boolean success) {
        this.success = success;
        return this;
    }

    public RPCMessageResponse createRPCMessageResponse() {
        return new RPCMessageResponse(type, destination, id, source, term, success, logIndex);
    }
}