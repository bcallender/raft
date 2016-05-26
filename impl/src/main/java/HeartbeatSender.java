import com.google.gson.Gson;

/**
 * Created by brandon on 5/25/16.
 */
public class HeartbeatSender implements Runnable {

    private Node parent;

    public HeartbeatSender(Node parent) {
        this.parent = parent;
    }

    @Override
    public void run() {
        AppendEntriesMessage aem = new AppendEntriesMessageBuilder()
                .setTerm(0)
                .createAppendEntriesMessage();
        Gson gson = new Gson();
        parent.sendToBroker(aem.serialize(gson));
        System.out.println("Test");
    }
}
