import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
    String leader;
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
                handleForwardRequest(msg);
                break;
            case REQUEST_FORWARD_RESPONSE:
                handleForwardRequestResponse(msg);
                break;
            case REQUEST_VOTE:
            case REQUEST_VOTE_RESPONSE:
            case GET:
                handleGetMessage(msg);
                break;
            case SET:
                handleSetMessage(msg);
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

    private void handleSetMessage(JSONObject msg) {
        String key = msg.getString("key");
        String value = msg.getString("value");
        store.put(key, value);
        JSONObject setResponse = new JSONObject();
        setResponse.put("type", MessageType.SET_RESPONSE)
                .put("id", msg.get("id"))
                .put("key", key)
                .put("value", value);
        brokerManager.sendToBroker(setResponse.toString().getBytes(Charset.defaultCharset()));
    }

    private void handleForwardRequestResponse(JSONObject msg) {
        String key = msg.getString("key");
        int id = msg.getInt("id");
        //is this reply stale? (my inFlight table has been flushed since i sent this)
        if (commandsInFlight.containsKey(id)) {
            //is this an error?
            commandsInFlight.remove(id);
            if (msg.has("error")) {
                ErrorMessage response = ErrorMessage.deserialize(msg.toString(), gson);
                ErrorMessage reply = new ErrorMessage(MessageType.GET_RESPONSE, null, id, this.nodeName, response.getError());
                brokerManager.sendToBroker(reply.serialize(gson));
            } else { //message has the data we need!
                Message m = new Message(MessageType.GET_RESPONSE, null, id, this.nodeName);
                JsonObject toSend = m.serializeToObject(gson);
                toSend.addProperty("key", key);
                toSend.addProperty("value", msg.getString("value"));
                brokerManager.sendToBroker(toSend.toString().getBytes(Charset.defaultCharset()));
            }
        }

        //drop message if stale
    }

    private void handleForwardRequest(JSONObject msg) {
        String key = msg.getString("key");
        int id = msg.getInt("id");
        String source = msg.getString("source");
        //am i the leader?
        if (this.role == Role.LEADER) {
            if (store.containsKey(key)) {
                Message m = new Message(MessageType.REQUEST_FORWARD_RESPONSE, source, id, this.nodeName);
                JsonObject reply = m.serializeToObject(gson);
                reply.addProperty("key", key);
                reply.addProperty("value", store.get(key));
                brokerManager.sendToBroker(reply.toString().getBytes(Charset.defaultCharset()));
            } else {
                ErrorMessage em = new ErrorMessage(MessageType.REQUEST_FORWARD_RESPONSE, source, id, this.nodeName,
                        String.format("No such key: %s", key));
                brokerManager.sendToBroker(em.serialize(gson));
            }
        } else {
            ErrorMessage em = new ErrorMessage(MessageType.REQUEST_FORWARD_RESPONSE, source, id, this.nodeName,
                    "Cannot identify leader -- no referenced at follower perceived leader");
            brokerManager.sendToBroker(em.serialize(gson));
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


            if (store.containsKey(key)) {

                Message m = new Message(MessageType.GET_RESPONSE, null, id, this.nodeName);
                JsonObject getResp = m.serializeToObject(gson);
                getResp.addProperty("key", key);
                getResp.addProperty("value", store.get(key));
                brokerManager.sendToBroker(getResp.toString().getBytes(Charset.defaultCharset()));

            } else {
                ErrorMessage em = new ErrorMessage(MessageType.GET_RESPONSE, null, id, this.nodeName,
                        String.format("No such key: %s", key));
                brokerManager.sendToBroker(em.serialize(gson));
            }

            commandsInFlight.remove(id);


        } else { //i'm not the leader, I need to get the value from the leader
            //do we know who the leader is?
            if (leader != null) {
                Message m = new Message(MessageType.REQUEST_FORWARD, leader, id, this.nodeName);
                JsonObject fwd = m.serializeToObject(gson);
                fwd.addProperty("key", key);
                brokerManager.sendToBroker(fwd.toString().getBytes(Charset.defaultCharset()));
            } else { //current leader unknown
                ErrorMessage em = new ErrorMessage(MessageType.GET_RESPONSE, null, id, this.nodeName,
                        String.format("Cannot identify Leader -- no reference at follower: %s", key));
                brokerManager.sendToBroker(em.serialize(gson));
                commandsInFlight.remove(id);
            }
        }
    }

    private enum Role {FOLLOWER, CANDIDATE, LEADER}
}
