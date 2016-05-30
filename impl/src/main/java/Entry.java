import java.io.Serializable;

/**
 * Created by brandon on 5/24/16.
 */
public class Entry implements Serializable {


    boolean applied;

    String key;
    String value;
    int term;
    int index;
    int requestId;

    public Entry(boolean applied, String key, String value, int term, int index, int requestId) {
        this.applied = applied;
        this.key = key;
        this.value = value;
        this.term = term;
        this.index = index;
        this.requestId = requestId;

    }

    public int getTerm() {
        return term;
    }

    public boolean conflictsWith(Entry e) {
        return e.index == this.index && e.term != this.term;
    }
    public boolean moreRecentThan(int otherTerm, int otherIdx) {
        return (term > otherTerm) || (otherTerm == term && index > otherIdx);
    }

}
