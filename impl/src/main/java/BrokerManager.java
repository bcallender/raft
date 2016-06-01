import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import java.nio.charset.Charset;
import java.util.List;

/**
 * The BrokerManager handles all inter-thread and node<-->chistributed communication. As in the sample code, there are two
 * sockets for communicating with chistributed, a subSock that receives messages from chistributed (eg SET, GET and others)
 * and a reqSock that sends messages to chistributed (and thus, other nodes). Unlike in the sample code however, the reqSock
 * is implemented as type DEALER rather than REQ -- to avoid the rigid restrictions that come with REQ-ROUTER relationships.
 * (The REQ socket will send only one message before it receives a reply; the DEALER is fully asynchronous. -- ZMQ docs).
 * Nodes can spawn threads when they need to send heartbearts or an election times out -- these threads need to send messages,
 * but ZMQ sockets are not threadsafe. To improve stability, we chose to use the worker PAIR socket pattern described in the ZMQ
 * docs, where the election/heartbeat threads do not send messages directly (as the main thread's socket identity is unique), but
 * instead pass the messages back to the main thread to be forwarded at the next poll. Benefits and drawbacks of this approach can
 * be found in the paper.
 */
public class BrokerManager {

    private final String heartbeatInproc; //the inter-thread uri for communication between heartbeat thread and main thread
    private final String electionInproc; // the inter-thread uri for communication between election thread and main thread
    private Node node;
    private ZContext context;
    private ZMQ.Socket subSock; //SUB socket for communicating with the chistributed Router
    private ZMQ.Socket reqSock; //DEALER socket for communicating with the chistributed router
    private ZMQ.Socket heartBeatSock; //PAIR thread for communicating with the heartbeat thread
    private ZMQ.Socket electionStartSocket; //PAIR thread for communicating with the election thread
    private ZMQ.Poller poller;
    private String pubEndpoint;
    private String routerEndpoint;
    private List<String> peers;
    private boolean debug;

    public BrokerManager(List<String> peers, String nodeName, String pubEndpoint, String routerEndpoint, boolean debug,
                         String startingRole, String startingLeader) {
        this.peers = peers;
        this.pubEndpoint = pubEndpoint;
        this.routerEndpoint = routerEndpoint;
        this.context = new ZContext();
        nodeName = nodeName.trim();

        subSock = this.context.createSocket(ZMQ.SUB);
        subSock.connect(this.pubEndpoint);
        subSock.subscribe(nodeName.getBytes());
        subSock.setIdentity(nodeName.getBytes());

        this.poller = new ZMQ.Poller(4); //establish poller for all 4 sockets
        this.poller.register(subSock, ZMQ.Poller.POLLIN);


        reqSock = this.context.createSocket(ZMQ.DEALER);
        //ZMQ DEALER type makes it easier to send asynchronously as opposed to ZMQ.REQ, messages don't need to be sent in lockstep
        reqSock.connect(this.routerEndpoint);
        reqSock.setIdentity(nodeName.getBytes());
        this.poller.register(reqSock);

        //the inter-thread uri for communication between heartbeat thread and main thread
        this.heartbeatInproc = "inproc://" + nodeName + ".heartbeat";
        //the inter-thread uri for communication between election thread and main thread
        this.electionInproc = "inproc://" + nodeName + ".election";

        this.heartBeatSock = this.context.createSocket(ZMQ.PAIR);
        this.heartBeatSock.bind(heartbeatInproc);


        this.poller.register(heartBeatSock, ZMQ.Poller.POLLIN);

        this.electionStartSocket = this.context.createSocket(ZMQ.PAIR);
        this.electionStartSocket.bind(electionInproc);


        this.poller.register(electionStartSocket, ZMQ.Poller.POLLIN);
        this.debug = debug;
        if (debug)
            Logger.setMasterLogLevel(Logger.LogLevel.DEBUG);
        else
            Logger.setMasterLogLevel(Logger.LogLevel.INFO);


        this.node = new Node(nodeName, this, startingRole, startingLeader);
    }

    private void handleBrokerMessage(ZMsg message) {

    }

    //sends message to broker
    public void sendToBroker(byte[] message) {
        byte[] nullFrame = new byte[0]; //need to send a null frame with DEALER to emulate REQ envelope
        this.reqSock.send(nullFrame, ZMQ.SNDMORE);
        this.reqSock.send(message, ZMQ.DONTWAIT);
        Logger.trace(String.format("Sent Message %s", new String(message, Charset.defaultCharset())));


    }

    //send message on PAIR socket between threads
    public void sendInterThread(byte[] message, ZMQ.Socket sock) { //send message on specified thread. used in heartbeat/election
        sock.send(message, ZMQ.NOBLOCK);
        Logger.trace(String.format("Sent Message %s", new String(message, Charset.defaultCharset())));
    }

    //get a new socket for heartbeat send
    public ZMQ.Socket getHeartBeatSock() { //create new socket for heartbeat thread to communicate with main thread
        ZMQ.Socket ss = context.createSocket(ZMQ.PAIR);
        ss.connect(heartbeatInproc);
        return ss;
    }

    //get a new socket for election send
    public ZMQ.Socket getElectionStartSocket() { //create new socket for election thread to communicate with main thread
        ZMQ.Socket ss = context.createSocket(ZMQ.PAIR);
        ss.connect(electionInproc);
        return ss;
    }

    //Main polling loop, continuously listening for new messages to pass on to node handlers
    public void start() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                poller.poll(1000);
            } catch (IllegalArgumentException ie) {
                Logger.trace("ZMQ Set an illegal timeout value in its poller");
            } catch (Exception ce) {
                /*I'd like to catch a ClosedByInterruptException here, which is caused by multithreaded operation
                 on the ZMQ socket, or a ClosedChannelException when an inprocess send/req is cancelled by a SIGINT but
                 since it's a checked exception and not technically thrown by anything in the call
                 stack, I cannot.
                 */
                Logger.trace(String.format("%s -- ZMQ Exception : %s", this.node.getNodeName(), ce.toString()));

            }


            //subSock registered at index '0' -- subSock
            if (poller.pollin(0)) {
                ZMsg msg = ZMsg.recvMsg(subSock, ZMQ.DONTWAIT);
                node.handleMessage(msg);
            }

            //reqSock registered at index '1' -- reqSock
            if (poller.pollin(1)) {
                ZMsg msg = ZMsg.recvMsg(reqSock, ZMQ.DONTWAIT);
                handleBrokerMessage(msg);
            }

            if (poller.pollin(2)) { //got on pair socket, forward directly to recipient
                byte[] msgToForward = heartBeatSock.recv();
                sendToBroker(msgToForward);
            }

            if (poller.pollin(3)) { //got on pair socket, forward directly to recipient
                byte[] msgToForward = electionStartSocket.recv();
                sendToBroker(msgToForward);
            }


            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        //ZMQ shutdown cleanup
        Logger.warning("Cleaning Up");
        context.destroy();
    }

    public List<String> getPeers() {
        return peers;
    }
}
