import org.json.JSONObject;
import org.zeromq.ZMsg;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created by techbar on 5/25/16.
 */
public class Node {

    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private BrokerManager brokerManager;
    private Map<String, String> store;
    private String nodeName;
    private boolean connected;

    public Node(String nodeName, BrokerManager brokerManager) {
        this.nodeName = nodeName;
        this.store = new HashMap<>();
        this.brokerManager = brokerManager;
        this.connected = false;

        final ScheduledFuture<?> heartBeatTimeout =
                this.executorService.scheduleAtFixedRate(new HeartbeatSender(brokerManager),
                        175,
                        175,
                        TimeUnit.MILLISECONDS);
    }

    public void handleMessage(ZMsg message) {
        assert (message.size() == 3);
        JSONObject msg = new JSONObject(message.getLast().toString());
        MessageType type = MessageType.parse(msg.getString("type"));

        switch (type) {
            case GET:
                String k = msg.getString("key");

                JSONObject m = new JSONObject();
                if (store.containsKey(k)) {
                    String val = store.get(k);
                    m.put("type", "getResponse");
                    m.put("id", msg.get("id"));
                    m.put("key", k);
                    m.put("value", val);
                } else {
                    m.put("type", "getResponse");
                    m.put("id", msg.get("id"));
                    m.put("error", String.format("No such key: %s", k));

                }
                brokerManager.sendToBroker(m.toString().getBytes(Charset.defaultCharset()));
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
                    brokerManager.log("BrokerManager Running");
                }
            case UNKNOWN:
        }
    }
}
