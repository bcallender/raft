import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import java.util.ArrayList;
import java.util.List;

/**
 * Main class that handles command line arguments and begins broker manager polling loop.
 */
public class Main {

    @Parameter(names = "--debug", description = "debug")
    boolean debug;
    @Parameter(names = "--pub-endpoint", description = "Public Endpoint")
    private String pubEndpoint = "tcp://127.0.0.1:23310";
    @Parameter(names = "--router-endpoint", description = "Router Endpoint")
    private String routerEndpoint = "tcp://127.0.0.1:23311";
    @Parameter(names = "--node-name", description = "BrokerManager Name")
    private String nodeName;
    @Parameter(names = "--peer", description = "Peers")
    private List<String> peers = new ArrayList<>();
    @Parameter(names = "--force-role", description = "Force a node into a starting role (default FOLLOWER)")
    private String startRole = "FOLLOWER";
    @Parameter(names = "--force-leader", description = "This node will startup thinking the leader is this")
    private String knownLeader = null;

    public static void main(String[] args) {
        Main main = new Main();
        new JCommander(main, args);
        BrokerManager n = new BrokerManager(main.peers, main.nodeName,
                main.pubEndpoint, main.routerEndpoint, main.debug, main.startRole, main.knownLeader);
        n.start();


    }
}
