import com.google.gson.Gson;
import org.json.JSONObject;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created by brandon on 5/13/16.
 */
public class Node {

    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private ZContext context;
    private ZMQ.Socket subSock;
    private ZMQ.Socket reqSock;
    private ZMQ.Poller poller;
    private Map<String, String> store;
    private String nodeName;
    private String pubEndpoint;
    private String routerEndpoint;
    private List<String> peers;
    private boolean debug;
    private boolean connected;
    private Gson gson;

    public Node(List<String> peers, String nodeName, String pubEndpoint, String routerEndpoint) {
        this.peers = peers;
        this.nodeName = nodeName;
        this.pubEndpoint = pubEndpoint;
        this.routerEndpoint = routerEndpoint;
        this.context = new ZContext();
        this.connected = false;

        subSock = this.context.createSocket(ZMQ.SUB);
        subSock.connect(pubEndpoint);
        subSock.subscribe(nodeName.getBytes());
        subSock.setIdentity(nodeName.getBytes());

        this.poller = new ZMQ.Poller(2);
        this.poller.register(subSock, ZMQ.Poller.POLLIN);


        reqSock = this.context.createSocket(ZMQ.DEALER);
        reqSock.connect(routerEndpoint);
        reqSock.setIdentity(nodeName.getBytes());
        this.poller.register(reqSock);


        this.debug = true;
        this.store = new HashMap<>();
        this.gson = new Gson();

        final ScheduledFuture<?> heartBeatTimeout =
                this.executorService.scheduleAtFixedRate(new HeartbeatSender(this), 175, 175, TimeUnit.MILLISECONDS);





    }

    public void handleBrokerMessage(ZMsg message) {

    }

    public void logDebug(String message) {
        if (this.debug)
            System.out.println(message);
    }

    public void log(String message) {
        System.out.println(message);
    }

    public void sendToBroker(byte[] message) {
        byte[] nullFrame = new byte[0]; //need to send a null frame with DEALER to emulate REQ envelope
        this.reqSock.send(nullFrame, ZMQ.SNDMORE);
        this.reqSock.send(message);

        logDebug(String.format("Sent Message %s", new String(message, Charset.defaultCharset())));


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
                sendToBroker(m.toString().getBytes(Charset.defaultCharset()));
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
                for (String peer : peers) {
                    JSONObject dupl = new JSONObject();
                    dupl.put("type", MessageType.DUPL)
                            .put("destination", peer)
                            .put("key", key)
                            .put("value", value);
                    sendToBroker(dupl.toString().getBytes());
                }
                JSONObject setResponse = new JSONObject();
                setResponse.put("type", MessageType.SET_RESPONSE)
                        .put("id", msg.get("id"))
                        .put("key", key)
                        .put("value", value);
                sendToBroker(setResponse.toString().getBytes(Charset.defaultCharset()));

                break;
            case HELLO:
                if (!this.connected) {
                    this.connected = true;
                    JSONObject hr = new JSONObject(String.format("{'type': 'helloResponse', 'source': %s}", this.nodeName));
                    sendToBroker(hr.toString().getBytes(Charset.defaultCharset()));
                    log("Node Running");
                }
            case UNKNOWN:
        }


    }


    public void start() {
        while (true) {
            poller.poll();

            //subSock registered at index '0'
            if (poller.pollin(0)) {
                ZMsg msg = ZMsg.recvMsg(subSock, ZMQ.DONTWAIT);
                handleMessage(msg);
            }

            //reqSock registered at index '1'
            if (poller.pollin(1)) {
                ZMsg msg = ZMsg.recvMsg(reqSock, ZMQ.DONTWAIT);
                handleBrokerMessage(msg);
            }

        }
    }

    public void shutdown() {
        this.subSock.close();
        this.reqSock.close();
    }


}
