import java.io.Serializable;

/**
 * Created by brandon on 5/24/16.
 */
public class Entry implements Serializable {


    boolean applied;
    boolean noop;

    String key;
    String value;
    int term;
    int index;
    int requestId;

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

    public boolean isNoop() {
        return noop;
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
