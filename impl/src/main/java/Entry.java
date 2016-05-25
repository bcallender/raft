import java.io.Serializable;

/**
 * Created by brandon on 5/24/16.
 */
public class Entry implements Serializable {


    boolean applied;
    EntryType entryType;
    String key;
    String value;

    public Entry(boolean applied, EntryType entryType, String key, String value) {
        this.applied = applied;
        this.entryType = entryType;
        this.key = key;
        this.value = value;
    }

    protected enum EntryType {SET, GET}


}
