import java.io.Serializable;

/**
 * Created by brandon on 5/24/16.
 */
public class RequestVoteMessage extends Message implements Serializable {

    private int term;
    private int candidateId;
    private int lastLogIndex;
    private int lastLogTerm;

    
    public RequestVoteMessage(int term, int candidateId, int lastLogIndex, int lastLogTerm) {
        this.term = term;
        this.candidateId = candidateId;
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
    }
}
