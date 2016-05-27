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

    public Entry(boolean applied, EntryType entryType, String key, String value, int term) {
        this.applied = applied;
        this.entryType = entryType;
        this.key = key;
        this.value = value;
        this.term = term;
    }

    protected enum EntryType {SET, GET}

    public boolean moreRecentThan(int otherTerm, int myIdx, int otherIdx) {
        return (otherTerm < term) || (otherTerm == term && myIdx > otherIdx);
    }

}
