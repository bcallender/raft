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

    private final String heartbeatInproc;
    private final String electionInproc;
    private Node node;
    private ZContext context;
    private ZMQ.Socket subSock;
    private ZMQ.Socket reqSock;
    private ZMQ.Socket heartBeatSock;
    private ZMQ.Socket electionStartSocket;
    private ZMQ.Poller poller;
    private String pubEndpoint;
    private String routerEndpoint;
    private List<String> peers;
    private boolean debug;
    private ReentrantLock reqSockLock;

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

        this.poller = new ZMQ.Poller(4);
        this.poller.register(subSock, ZMQ.Poller.POLLIN);


        reqSock = this.context.createSocket(ZMQ.DEALER);
        //ZMQ DEALER type makes it easier to send asynchronously as opposed to ZMQ.REQ, messages don't need to be sent in lockstep
        reqSock.connect(this.routerEndpoint);
        reqSock.setIdentity(nodeName.getBytes());
        this.poller.register(reqSock);
        this.reqSockLock = new ReentrantLock();

        this.heartbeatInproc = "inproc://" + nodeName + ".heartbeat";
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


    public void sendToBroker(byte[] message) {
        byte[] nullFrame = new byte[0]; //need to send a null frame with DEALER to emulate REQ envelope
        this.reqSock.send(nullFrame, ZMQ.SNDMORE);
        this.reqSock.send(message, ZMQ.DONTWAIT);



    }

    public void sendInterThread(byte[] message, ZMQ.Socket sock) {
        sock.send(message, ZMQ.NOBLOCK);
        Logger.trace(String.format("Sent Message %s", new String(message, Charset.defaultCharset())));
    }

    public ZMQ.Socket getHeartBeatSock() {
        ZMQ.Socket ss = context.createSocket(ZMQ.PAIR);
        ss.connect(heartbeatInproc);
        return ss;
    }

    public ZMQ.Socket getElectionStartSocket() {
        ZMQ.Socket ss = context.createSocket(ZMQ.PAIR);
        ss.connect(electionInproc);
        return ss;
    }

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


            //subSock registered at index '0'
            if (poller.pollin(0)) {
                ZMsg msg = ZMsg.recvMsg(subSock, ZMQ.DONTWAIT);
                node.handleMessage(msg);
            }

            //reqSock registered at index '1'
            if (poller.pollin(1)) {
                ZMsg msg = ZMsg.recvMsg(reqSock, ZMQ.DONTWAIT);
                handleBrokerMessage(msg);
            }

            if (poller.pollin(2)) { //got on pair socket, forward
                byte[] msgToForward = heartBeatSock.recv();
                sendToBroker(msgToForward);
            }

            if (poller.pollin(3)) { //got on pair socket, forward
                byte[] msgToForward = electionStartSocket.recv();
                sendToBroker(msgToForward);
            }


            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Logger.warning("Exiting Loop");
        context.destroy();
    }

    public List<String> getPeers() {
        return peers;
    }
}
