import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by brandon on 5/20/16.
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

    public static void main(String[] args) {
        Main main = new Main();
        new JCommander(main, args);
        BrokerManager n = new BrokerManager(main.peers, main.nodeName,
                main.pubEndpoint, main.routerEndpoint, main.debug);
        n.start();


    }
}
