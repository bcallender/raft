import java.util.List;

public class RequestVoteMessageBuilder {
    private final MessageType type = MessageType.REQUEST_VOTE;
    private List<String> destination;
    private int id;
    private String source;
    private int term;
    private String candidateId;
    private int lastLogIndex;
    private int lastLogTerm;

    public RequestVoteMessageBuilder setDestination(List<String> destination) {
        this.destination = destination;
        return this;
    }

    public RequestVoteMessageBuilder setId(int id) {
        this.id = id;
        return this;
    }

    public RequestVoteMessageBuilder setSource(String source) {
        this.source = source;
        return this;
    }

    public RequestVoteMessageBuilder setTerm(int term) {
        this.term = term;
        return this;
    }

    public RequestVoteMessageBuilder setCandidateId(String candidateId) {
        this.candidateId = candidateId;
        return this;
    }

    public RequestVoteMessageBuilder setLastLogIndex(int lastLogIndex) {
        this.lastLogIndex = lastLogIndex;
        return this;
    }

    public RequestVoteMessageBuilder setLastLogTerm(int lastLogTerm) {
        this.lastLogTerm = lastLogTerm;
        return this;
    }

    public RequestVoteMessage createRequestVoteMessage() {
        return new RequestVoteMessage(type, destination, id, source, term, candidateId, lastLogIndex, lastLogTerm);
    }
}