import com.google.gson.Gson;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by brandon on 5/13/16.
 */
public class BrokerManager {

    private Node node;
    private ZContext context;
    private ZMQ.Socket subSock;
    private ZMQ.Socket reqSock;
    private ZMQ.Poller poller;
    private String pubEndpoint;
    private String routerEndpoint;
    private List<String> peers;
    private boolean debug;
    private Gson gson;
    private ReentrantLock reqSockLock;

    public BrokerManager(List<String> peers, String nodeName, String pubEndpoint, String routerEndpoint) {
        this.peers = peers;
        this.pubEndpoint = pubEndpoint;
        this.routerEndpoint = routerEndpoint;
        this.context = new ZContext();

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
        this.reqSockLock = new ReentrantLock();


        this.debug = true;
        this.gson = new Gson();

        this.node = new Node(nodeName, this);
    }

    private void handleBrokerMessage(ZMsg message) {

    }

    public void logDebug(String message) {
        if (this.debug)
            System.out.println(message);
    }

    public void log(String message) {
        System.out.println(message);
    }

    public void sendToBroker(byte[] message) {
        reqSockLock.lock();
        byte[] nullFrame = new byte[0]; //need to send a null frame with DEALER to emulate REQ envelope
        this.reqSock.send(nullFrame, ZMQ.SNDMORE);
        this.reqSock.send(message);
        reqSockLock.unlock();

        logDebug(String.format("Sent Message %s", new String(message, Charset.defaultCharset())));


    }


    public void start() {
        while (true) {
            poller.poll();

            //subSock registered at index '0'
            if (poller.pollin(0)) {
                ZMsg msg = ZMsg.recvMsg(subSock, ZMQ.DONTWAIT);
                //Node.handleMessage(this, msg);
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

    public List<String> getPeers() {
        return peers;
    }
}
