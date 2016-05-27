import java.io.Serializable;

/**
 * Created by brandon on 5/24/16.
 */
public class Entry implements Serializable {


    boolean applied;
    EntryType entryType;

    String key;
    String value;
    int term;
    int index;

    public int getTerm() {
        return term;
    }

    public Entry(boolean applied, EntryType entryType, String key, String value, int term, int index) {
        this.applied = applied;
        this.entryType = entryType;
        this.key = key;
        this.value = value;
        this.term = term;
        this.index = index;

    }

    public boolean conflictsWith(Entry e) {
        return e.index == this.index && e.term != this.term;
    }
    public boolean moreRecentThan(int otherTerm, int otherIdx) {
        return (otherTerm < term) || (otherTerm == term && index > otherIdx);
    }

    protected enum EntryType {SET, GET}

}
