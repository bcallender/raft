import com.google.gson.Gson;
import org.json.JSONObject;
import org.zeromq.ZMsg;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Created by techbar on 5/25/16.
 */
public class Node implements Serializable {

    private static final int HEARTBEAT_INTERVAL = 50;

    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private final ScheduledFuture<?> heartBeatSend;
    private final ScheduledFuture<?> electionTimeout;
    //volatile on all
    int commitIndex;
    int lastApplied;
    Role role;
    //volatile on master
    int nextIndex;
    int matchIndex;
    List<Entry> log;
    private Gson gson;
    private HashMap<Integer, ClientCommand> commandsInFlight;
    private BrokerManager brokerManager;
    private Map<String, String> store;
    private String nodeName;
    private boolean connected;
    //persistent
    private int currentTerm;
    private String votedFor;

    public Node(String nodeName, BrokerManager brokerManager) {
        this.nodeName = nodeName;
        this.store = new HashMap<>();
        this.brokerManager = brokerManager;
        this.connected = false;
        this.currentTerm = 0;
        this.votedFor = null;
        this.log = new ArrayList<>();
        this.commitIndex = 0;
        this.lastApplied = 0;
        this.role = Role.FOLLOWER;
        this.nextIndex = 0;
        this.matchIndex = 0;


        int heartBeatTimeoutValue = ThreadLocalRandom.current().nextInt(150, 301);

        this.heartBeatSend =
                this.executorService.scheduleAtFixedRate(new HeartbeatSender(brokerManager),
                        HEARTBEAT_INTERVAL,
                        HEARTBEAT_INTERVAL,
                        TimeUnit.MILLISECONDS);

        this.electionTimeout =
                this.executorService.scheduleAtFixedRate(new ElectionTimeoutHandler(),
                        heartBeatTimeoutValue,
                        heartBeatTimeoutValue,
                        TimeUnit.MILLISECONDS);
        Logger.debug(String.format("Election timeout value for %s is %d", nodeName, heartBeatTimeoutValue));
        heartBeatSend.cancel(true);

        this.gson = new Gson();


    }

    public void handleMessage(ZMsg message) {
        assert (message.size() == 3);
        JSONObject msg = new JSONObject(message.getLast().toString());
        MessageType type = MessageType.parse(msg.getString("type"));

        switch (type) {
            case APPEND_ENTRIES:
            case APPEND_ENTRIES_RESPONSE:
            case REQUEST_FORWARD:
            case REQUEST_FORWARD_RESPONSE:
            case REQUEST_VOTE:
            case REQUEST_VOTE_RESPONSE:
            case GET:
                handleGetMessage(msg);
                break;
            case DUPL:
                String key = msg.getString("key");
                String value = msg.getString("value");
                store.put(key, value);
                break;
            case SET:
                key = msg.getString("key");
                value = msg.getString("value");
                store.put(key, value);
                for (String peer : brokerManager.getPeers()) {
                    JSONObject dupl = new JSONObject();
                    dupl.put("type", MessageType.DUPL)
                            .put("destination", peer)
                            .put("key", key)
                            .put("value", value);
                    brokerManager.sendToBroker(dupl.toString().getBytes());
                }
                JSONObject setResponse = new JSONObject();
                setResponse.put("type", MessageType.SET_RESPONSE)
                        .put("id", msg.get("id"))
                        .put("key", key)
                        .put("value", value);
                brokerManager.sendToBroker(setResponse.toString().getBytes(Charset.defaultCharset()));

                break;
            case HELLO:
                if (!connected) {
                    connected = true;
                    JSONObject hr = new JSONObject(String.format("{'type': 'helloResponse', 'source': %s}", nodeName));
                    brokerManager.sendToBroker(hr.toString().getBytes(Charset.defaultCharset()));
                    Logger.info("BrokerManager Running");
                }
            case UNKNOWN:
        }
    }

    private void handleGetMessage(JSONObject msg) {

        String key = msg.getString("key");
        int id = msg.getInt("id");
        ClientCommand command = new ClientCommand(MessageType.GET, key, null);
        commandsInFlight.put(id, command);
        //am I the leader?
        if (this.role == Role.LEADER) {
            //return the latest value from the store TODO:check if still leader?

            JSONObject getResp = new JSONObject();
            getResp.put("type", MessageType.GET_RESPONSE);
            getResp.put("id", id);
            if (store.containsKey(key)) {
                getResp.put("key", key);
                getResp.put("value", store.get(key));
            } else {
                getResp.put("error", String.format("No such key: %s", key));
            }

            brokerManager.sendToBroker(getResp.toString().getBytes(Charset.defaultCharset()));
            commandsInFlight.remove(id);


        } else { //i'm not the leader, I need to get the value from the leader
            //do we know who the leader is?
            if (true) { //TODO: FIX
                JSONObject fwd = new JSONObject();
                fwd.put("type", MessageType.REQUEST_FORWARD);
                fwd.put("id", id);
                fwd.put("key", key);
                fwd.put("source", this.nodeName);
            } else { //current leader unknown
                JSONObject getResp = new JSONObject();
                getResp.put("type", MessageType.GET_RESPONSE);
                getResp.put("id", id);
                getResp.put("error", "Cannot identify leader");
                brokerManager.sendToBroker(getResp.toString().getBytes(Charset.defaultCharset()));
                commandsInFlight.remove(id);
            }
        }
    }

    private enum Role {FOLLOWER, CANDIDATE, LEADER}
}
