import jdk.nashorn.internal.ir.debug.JSONWriter;
import jdk.nashorn.internal.parser.JSONParser;
import org.zeromq.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by brandon on 5/13/16.
 */
public class Node {

    private String nodeName;
    private String pubEndpoint;
    private String routerEndpoint;
    private List<String> peers;
    private boolean debug;
    ZContext context;
    private boolean connected;
    ZMQ.Socket subSock;
    ZMQ.Socket reqSock;
    ZMQ.Poller poller;
    Map<String, String> store;

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


        reqSock = this.context.createSocket(ZMQ.REQ);
        reqSock.connect(routerEndpoint);
        reqSock.setIdentity(nodeName.getBytes());
        this.poller.register(reqSock, ZMQ.Poller.POLLIN);


        this.debug = true;
        this.store = new HashMap<>();


    }

    public void handleBrokerMessage(ZMsg message){

    }

    public void handleMessage(ZMsg message) {
        assert(message.size() == 3);


    }



    public void start() {
        while(true) {
            poller.poll();

            //subSock registered at index '0'
            if( poller.pollin(0)) {
               ZMsg msg = ZMsg.recvMsg(subSock, ZMQ.DONTWAIT);
                handleMessage(msg);
            }

            //reqSock registered at index '1'
            if( poller.pollin(1)) {
                ZMsg msg = ZMsg.recvMsg(reqSock, ZMQ.DONTWAIT);
                handleBrokerMessage(msg);
            }

        }
    }

    public void shutdown(){
        this.subSock.close();
        this.reqSock.close();
    }



}
