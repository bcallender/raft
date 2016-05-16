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
    ZMQQueue sub;
    ZLoop loop;
    Map<String, String> store;

    public Node(List<String> peers, String nodeName, String pubEndpoint, String routerEndpoint) {
        this.peers = peers;
        this.nodeName = nodeName;
        this.pubEndpoint = pubEndpoint;
        this.routerEndpoint = routerEndpoint;
        this.context = new ZContext();
        this.connected = false;
        this.loop = new ZLoop();

        subSock = this.context.createSocket(ZMQ.SUB);
        subSock.connect(pubEndpoint);
        subSock.subscribe(nodeName.getBytes());
        subSock.setIdentity(nodeName.getBytes());

        reqSock = this.context.createSocket(ZMQ.REQ);
        reqSock.connect(routerEndpoint);
        reqSock.setIdentity(nodeName.getBytes());

        this.debug = true;
        this.store = new HashMap<>();

        String msg = "";
        while (!msg.equalsIgnoreCase("END")) {

            msg = new String(subSock.recv(0));
            System.out.println(msg);
        }





    }



}
