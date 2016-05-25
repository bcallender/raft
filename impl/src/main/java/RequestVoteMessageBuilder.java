public class RequestVoteMessageBuilder {
    private int term;
    private int candidateId;
    private int lastLogIndex;
    private int lastLogTerm;

    public RequestVoteMessageBuilder setTerm(int term) {
        this.term = term;
        return this;
    }

    public RequestVoteMessageBuilder setCandidateId(int candidateId) {
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
        return new RequestVoteMessage(term, candidateId, lastLogIndex, lastLogTerm);
    }
}