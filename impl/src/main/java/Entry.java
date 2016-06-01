import java.io.Serializable;

/**
 * Entry in the log of a raft node. Contains two special fields (noop and applied) which are used in our implmentation of
 * leader elections and commiting entires to stable storage respectively. Also contains the requestId of the client command
 * to make sure that responses are always sent to requests, regardless of leader elections.
 */
public class Entry implements Serializable {


    private boolean applied;
    private boolean noop;

    private String key;
    private String value;
    private int term;
    private int index;
    private int requestId;

    public Entry(boolean applied, String key, String value, int term, int index, int requestId, boolean noop) {
        this.applied = applied;
        this.key = key;
        this.value = value;
        this.term = term;
        this.index = index;
        this.requestId = requestId;
        this.noop = noop;

    }

    public int getTerm() {
        return term;
    }

    public boolean isApplied() {
        return applied;
    }

    public void setApplied(boolean applied) {
        this.applied = applied;
    }

    public boolean isNoop() {
        return noop;
    }

    public void setNoop(boolean noop) {
        this.noop = noop;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public int getIndex() {
        return index;
    }

    public int getRequestId() {
        return requestId;
    }

    public boolean conflictsWith(Entry e) {
        return e.index == this.index && e.term != this.term;
    }
    public boolean moreRecentThan(int otherTerm, int otherIdx) {
        return (term > otherTerm) || (otherTerm == term && index > otherIdx);
    }


    @Override
    public String toString() {
        return "Entry{" +
                "applied=" + applied +
                ", noop=" + noop +
                ", key='" + key + '\'' +
                ", value='" + value + '\'' +
                ", term=" + term +
                ", index=" + index +
                '}';
    }
}
