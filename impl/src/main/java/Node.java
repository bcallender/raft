import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.json.JSONException;
import org.json.JSONObject;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import java.io.File;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.*;

/**
 * The node class implements all logic needed at the node level in the Raft paper. A dispatcher in handleMessage
 * sends messages to handler functions, which are defined for all valid message types. Uses a scheduled threadPool to
 * handle sending heartbeats and timing out on election timeouts.
 */
public class Node {

    public static final int electionLowerBound = 500;
    public static final int electionUpperBound = 1000;
    private static final Charset CHARSET = Charset.defaultCharset();
    private static final int HEARTBEAT_INTERVAL = 150; //milliseconds between heartbeats
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    //we use a scheduled executor service to schedule/execute heartbeats and election timers, spawning new threads
    //when the timers complete
    private ScheduledFuture<?> heartBeatSend;
    private ScheduledFuture<?> electionTimeout;
    private int heartBeatTimeoutValue; //amount of time to wait for a heartbeat before timing out
    private Gson gson; //gson instance for parsing/generating JSON.
    private Map<Integer, ClientCommand> commandsInFlight; //commands issued to this node -- used to keep track of progress
    //and reply to GET commands.
    private BrokerManager brokerManager;
    private Map<String, String> store; //Any datastore we use must implement the Map interface. We use MapDB for persistent
    //storage to disk, it implements this interface and supports transactions and rollbacks.
    private String nodeName;
    private boolean connected;
    private DB db; //MapDB instance used for creating databases
    private int quorum; //number of nodes needed for quorum
    //volatile on all -- standard raft
    private int commitIndex;
    private Role role;
    private String leader;
    //volatile on master -- standard raft
    private Map<String, Integer> nextIndex; //maps instead of arrays for easier use.
    private Map<String, Integer> matchIndex;
    private List<Entry> log;
    //persistent
    private int currentTerm;
    private String votedFor;
    //requestVote State
    private HashMap<String, Boolean> voteResponses; //keep track of responses so we know if we've been elected leader
    public Node(String nodeName, BrokerManager brokerManager, String startRole, String startingLeader) {
        this.nodeName = nodeName;
        this.brokerManager = brokerManager;
        this.connected = false;
        this.currentTerm = 0;
        this.votedFor = null;
        this.log = new CopyOnWriteArrayList<>(); /*sometimes (very rarely) we concurrently add log entries while reading
        with an iterator. This is probably overkill, but solves our ConcurrentModification exception. */
        this.commitIndex = 0;
        this.role = Role.FOLLOWER;
        this.nextIndex = new TreeMap<>();
        this.matchIndex = new TreeMap<>();
        this.commandsInFlight = new ConcurrentHashMap<>();
        this.voteResponses = new HashMap<>();
        this.heartBeatTimeoutValue = ThreadLocalRandom.current().nextInt(electionLowerBound, electionUpperBound);
        this.gson = new Gson();
        this.quorum = (brokerManager.getPeers().size() + 1) / 2;

        //file backed db.
        try {
            File dbFile = File.createTempFile(this.nodeName, ".tmpDB"); //creates a temporary database file
            //in production, we might specify this as the same file every time -- that way we could support true
            //fail-recover behavior, all entries reported as committed are guarunteed to be placed on stable storage.
            this.db = DBMaker.fileDB(dbFile)
                    .fileMmapEnableIfSupported()
                    .closeOnJvmShutdown()
                    .transactionDisable() //we always commit after writes
                    .asyncWriteEnable()
                    .make();
            this.store = db.hashMap(this.nodeName);
        } catch (IOException e) {
            Logger.error("IO error creating db file");
            Logger.error(e.getMessage());
            System.exit(-1);
        }


        Entry e = new Entry(true, null, null, 0, 0, 0, true); // no op
        this.log.add(e); //begin log with a no op so the first true index is 1 (as in raft)

        if (startingLeader != null) {
            this.leader = startingLeader;
        }

        //logic for forcing a node to be a leader
        try {
            Role startingRole = Role.valueOf(startRole.toUpperCase());
            if (startingRole == Role.LEADER)
                currentTerm++;
            transitionTo(startingRole);
        } catch (IllegalArgumentException iae) {
            Logger.error(String.format("Invalid starting role: %s, starting as FOLLOWER", startRole));
            transitionTo(Role.FOLLOWER);
        }

    }

    String getNodeName() {
        return nodeName;
    }

    void handleMessage(ZMsg message) {
        JSONObject msg = null;
        MessageType type = MessageType.UNKNOWN;
        try {
            msg = new JSONObject(message.getLast().toString());
            type = MessageType.parse(msg.getString("type")); //parse type out of message
        } catch (NullPointerException | JSONException je) { //malformed json or null message.
            if (message != null)
                Logger.warning(String.format("Received malformed message : %s", message.toString()));
            else
                Logger.warning("Received malformed message : null");
        }

        switch (type) {
            case APPEND_ENTRIES:
                if (connected)
                    handleAppendEntries(msg);
                break;
            case APPEND_ENTRIES_RESPONSE:
                if (connected)
                    handleAppendEntriesResponse(msg);
                break;
            case REQUEST_VOTE:
                if (connected)
                    handleRequestVote(msg);
                break;
            case REQUEST_VOTE_RESPONSE:
                if (connected)
                    handleRequestVoteResponse(msg);
                break;
            case GET_REQUEST_FORWARD: //fallthrough to GET
            case GET:
                if (connected)
                    handleGetMessage(msg);
                break;
            case SET_REQUEST_FORWARD: //fallthrough to SET
            case SET:
                if (connected)
                    handleSetMessage(msg);
                break;
            case HELLO:
                if (!connected) {
                    connected = true;
                    Message m = new Message(MessageType.HELLO_RESPONSE, null, 0, nodeName);
                    brokerManager.sendToBroker(m.serialize(gson));
                    Logger.info(this.nodeName + " BrokerManager Running");
                    if (this.role == Role.LEADER) //forced to be leader
                        restartHeartBeatTimeout();
                    else
                        startElectionTimeout();
                }
            case UNKNOWN:
        }
    }

    //updates state to newTerm, does nothing if newTerm is stale, transition to follower if your term is forcibly updated
    private void updateTerm(int newTerm) {
        if (newTerm > currentTerm) {
            votedFor = null;
            currentTerm = newTerm;
            transitionTo(Role.FOLLOWER);
        }
    }

    /* handler for SET messages. if we are the leader, put the command in the commandsInFlight, append a new entry to the log
    * check at the next appendEntriesResponse whether or not we can reply to the client that their SET has succeeded.
    * If you aren't the leader, forward the request to the node you think is the leader -- eventually a leader will get
     * the request and start processing it, or a node that does not have a leader link to follow will reply with an error. */
    private void handleSetMessage(JSONObject msg) {
        try {
            String key = msg.getString("key");
            String value = msg.getString("value");
            int id = msg.getInt("id");

            if (this.role == Role.LEADER) {
                commandsInFlight.put(msg.getInt("id"), new ClientCommand(MessageType.SET, key, value));
                Entry entry = new Entry(false, key, value, currentTerm, log.size(), id, false);
                log.add(entry);

            } else {
                if (leader != null) {
                    Message setForward = new Message(MessageType.SET_REQUEST_FORWARD, leader, id, this.nodeName);
                    JsonObject fwd = setForward.serializeToObject(gson); /*convert to intermediate JsonObject to add
                    more custom fields. */
                    fwd.addProperty("key", key);
                    fwd.addProperty("value", value);
                    brokerManager.sendToBroker(fwd.toString().getBytes(CHARSET));
                } else { //current leader unknown
                    ErrorMessage em = new ErrorMessage(MessageType.SET_RESPONSE, null, id, this.nodeName,
                            String.format("Cannot identify Leader -- no reference at follower: %s", this.nodeName));
                    brokerManager.sendToBroker(em.serialize(gson));
                    commandsInFlight.remove(id);
                }
            }
        } catch (JSONException e) { //invalid json received
            Logger.warning(String.format("Invalid set received at %s", this.nodeName));
            ErrorMessage em = new ErrorMessage(MessageType.SET_RESPONSE, null, msg.getInt("id"), this.nodeName,
                    String.format("You sent me invalid JSON: %s", msg.toString()));
            brokerManager.sendToBroker(em.serialize(gson));
        }


    }

    /* handler for GET messages. if we are the leader, add it to our commandsInFlight and process on the next heartBeat
   * If you aren't the leader, forward the request to the node you think is the leader -- eventually a leader will get
    * the request and start processing it, or a node that does not have a leader link to follow will reply with an error. */
    private void handleGetMessage(JSONObject msg) {
        try {
            String key = msg.getString("key");
            int id = msg.getInt("id");

            //am I the leader?
            if (this.role == Role.LEADER) {
                ClientCommand command = new ClientCommand(MessageType.GET, key, null);
                commandsInFlight.put(id, command);

            } else { //i'm not the leader, I need to get the value from the leader
                //do we know who the leader is?
                if (leader != null) {
                    Message m = new Message(MessageType.GET_REQUEST_FORWARD, leader, id, this.nodeName);
                    JsonObject fwd = m.serializeToObject(gson);
                    fwd.addProperty("key", key);
                    brokerManager.sendToBroker(fwd.toString().getBytes(CHARSET));
                } else { //current leader unknown
                    ErrorMessage em = new ErrorMessage(MessageType.GET_RESPONSE, null, id, this.nodeName,
                            String.format("Cannot identify Leader -- no reference at follower: %s", key));
                    brokerManager.sendToBroker(em.serialize(gson));
                    commandsInFlight.remove(id);
                }
            }
        } catch (JSONException je) {
            Logger.warning(String.format("Invalid GET received at %s", this.nodeName));
            ErrorMessage em = new ErrorMessage(MessageType.GET_RESPONSE, null, msg.getInt("id"), this.nodeName,
                    String.format("You sent me invalid JSON: %s", msg.toString()));
            brokerManager.sendToBroker(em.serialize(gson));
        }
    }

    /* handler for requestVote messages. Basically follow the protocol specified in the raft paper */
    private void handleRequestVote(JSONObject msg) {
        RequestVoteMessage m = RequestVoteMessage.deserialize(msg.toString().getBytes(CHARSET), gson);
        int voteTerm = m.getTerm();
        String candidateId = m.getCandidateId();
        boolean success = false;
        //if the message is a later term than ours or we have yet to vote
        if (voteTerm > currentTerm || (voteTerm == currentTerm && (votedFor == null || votedFor.equals(candidateId)))) {
            updateTerm(voteTerm);
            int logTerm = m.getLastLogTerm();
            int logIndex = m.getLastLogIndex();
            Entry lastEntry = getLastEntry();
            //if log is empty then the other is vacuously up to date, otherwise compare them for recency
            if (lastEntry == null || (!lastEntry.moreRecentThan(logTerm, logIndex))) {
                votedFor = candidateId;
                success = true;
                restartElectionTimeout();
            }
        }
        //send result
        RPCMessageResponseBuilder response = new RPCMessageResponseBuilder();
        response.setDestination(m.getSource());
        response.setSource(nodeName);
        response.setTerm(voteTerm);
        response.setType(MessageType.REQUEST_VOTE_RESPONSE);
        response.setSuccess(success);
        brokerManager.sendToBroker(response.createRPCMessageResponse().serialize(gson));
    }

    /* handler for requestVoteResponse messages. Basically follow the protocol specified in the raft paper except
     * for a small optimization. If you lose an election by receiving a quorum of NO votes (not just by not receiving enough votes)
      * It is improbable (I believe impossible, but I can't prove it) that you will never be able to become a leader for
      * this term, as your log is behind a majority of nodes in the cluster. You should stop trying to become the leader.*/
    private void handleRequestVoteResponse(JSONObject msg) {

        RPCMessageResponse response = RPCMessageResponse.deserialize(msg.toString().getBytes(CHARSET),
                gson);
        if (this.role == Role.CANDIDATE) {
            this.voteResponses.put(response.getSource(), response.success);
            int numYeas = 0;
            int numNays = 0;
            if (voteResponses.size() > quorum) { //we have enough votes to check for quorum
                for (Boolean vote : voteResponses.values()) {
                    if (vote)
                        numYeas++;
                    else
                        numNays++;
                }
                if (numYeas > quorum) { //success, i am the leader now
                    Logger.info(String.format("Node %s received a quorum of votes. It is now the leader", this.nodeName));
                    transitionTo(Role.LEADER);
                } else if (numNays > quorum) { //failed quorum, restart election
                    Logger.info(String.format("Node %s did not receive a quorum of votes. Split Vote...", this.nodeName));
                    this.electionTimeout.cancel(true);
                    /* if you can't become the leader now, there's no way you're going to become the leader in this election
                    * so you should stop trying*/

                }
            }
        } else {
            Logger.info(String.format("%s received extraneous request vote response from %s:%s", this.nodeName,
                    response.getSource(), response.success));
        }



    }

    /* handler for appendEntries messages. Basically follow the protocol specified in the raft paper. Send failures to a client
     * if a log entry is erased, as that entry will not be committed and was rejected by a new leader. */
    private void handleAppendEntries(JSONObject msg) {
        AppendEntriesMessage m = AppendEntriesMessage.deserialize(msg.toString().getBytes(CHARSET), gson);
        boolean success = false;
        if (currentTerm <= m.getTerm()) {
            this.leader = m.getSource();
            updateTerm(m.getTerm());
            int nextIndex = m.getPrevLogIndex();
            if (nextIndex < log.size()) {
                Entry myEntry = log.get(nextIndex);
                if (myEntry.getTerm() == m.getPrevLogTerm()) {
                    success = true;
                    List<Entry> entries = m.getEntries();
                    if (!log.isEmpty()) {
                        List<Entry> logsToDelete = new ArrayList<>(log);
                        List<Entry> logsToKeep = log.subList(0, nextIndex + 1);
                        //keep logs up till prevLogIndex.
                        logsToDelete.removeAll(logsToKeep);
                        //logs to delete is all logs to be deleted
                        log = logsToKeep;
                        failOverwrittenLogEntries(logsToDelete);
                    }
                    log.addAll(entries);

                    int leaderCommit = m.getLeaderCommit();
                    if (leaderCommit > commitIndex) {
                        //commit entries that have been committed by master
                        updateCommitIndex(Math.min(leaderCommit, log.size() - 1));
                    }
                }
            }
            restartElectionTimeout();
        }
        //send result
        RPCMessageResponseBuilder response = new RPCMessageResponseBuilder();
        response.setDestination(m.getSource());
        response.setSource(nodeName);
        response.setTerm(currentTerm);
        response.setType(MessageType.APPEND_ENTRIES_RESPONSE);
        response.setSuccess(success);
        response.setLogIndex(log.size() - 1);
        brokerManager.sendToBroker(response.createRPCMessageResponse().serialize(gson));
    }

    /* handler for appendEntries messages. Basically follow the protocol specified in the raft paper. update the
     * state of GET requests in flight once we have reassurance from the rest of the cluster that we are still the leader */
    private void handleAppendEntriesResponse(JSONObject msg) {
        RPCMessageResponse m = RPCMessageResponse.deserialize(msg.toString().getBytes(Charset.defaultCharset()), gson);
        updateTerm(m.term); //if the term changes we swap to follower and exit
        if (role == Role.LEADER) {
            if (m.success) { //on success update recorded state for that node
                matchIndex.put(m.getSource(), m.logIndex);
                nextIndex.put(m.getSource(), m.logIndex + 1);
            } else { //on failure decrement relevant nextIndex for next send
                Integer next = nextIndex.get(m.getSource()) - 1;
                if (next < 0) {
                    Logger.error(String.format("%s has a negative nextIndex", m.getSource()));
                }
                nextIndex.put(m.getSource(), next);
            }

            updateGetRequests(m.getSource());
        } else {
            //drop message if not leader
            Logger.info(String.format("%s received extraneous append entries response from %s:%s", this.nodeName,
                    m.getSource(), m.success));
        }

    }

    /* compute values for sending heartbeats and construct appendEntries.  Basically follow the protocol specified in
        the raft paper. figures out the value of N as in figure 2 to figure out whether or not we can commit new log entries
        follow the logic as described in raft for incrementing or decrementing match/nextIndex and build appendEntries.
        Try to see if we can commit anything new on the master (we have quorum consensus on a new commitIndex)*/
    void sendHeartbeats(ZMQ.Socket threadedSocket) throws ClosedByInterruptException {
        int n; //as described in Fig. 2
        for (n = commitIndex+1; n < log.size(); n++) {
            if (log.get(n).getTerm() == currentTerm)
                break;
        }
        int acceptedCount = 1;
        for (String peer : brokerManager.getPeers()) {
            if (matchIndex.get(peer) >= n) {
                acceptedCount++; //we have accepted on this node the op at index n
            }
            List<Entry> newEntries = new ArrayList<>();
            if (!log.isEmpty()) {
                if (nextIndex.get(peer) < 0) {
                    Logger.error(String.format("%s has a negative nextIndex(%d) for %s", nodeName, nextIndex.get(peer), peer));
                } else if (nextIndex.get(peer) > log.size()) {
                    Logger.error(String.format("%s has a large nextIndex(%d) for %s", nodeName, nextIndex.get(peer), peer));
                } else
                    newEntries = (log.subList(nextIndex.get(peer), log.size()));
            }
            //Logger.info("found newEntries for " + peer);
            AppendEntriesMessageBuilder aemb = new AppendEntriesMessageBuilder()
                    .setTerm(currentTerm)
                    .setDestination(peer)
                    .setLeaderCommit(commitIndex)
                    .setSource(this.nodeName)
                    .setEntries(newEntries)
                    .setLeaderId(this.nodeName)
                    .setPrevLogIndex(nextIndex.get(peer) - 1)
                    .setPrevLogTerm(log.get(nextIndex.get(peer) - 1).getTerm());
            AppendEntriesMessage aem = aemb.createAppendEntriesMessage();
            brokerManager.sendInterThread(aem.serialize(gson), threadedSocket);
        }

        if (acceptedCount > (brokerManager.getPeers().size() + 1) / 2 && log.get(n).getTerm() == currentTerm && n > commitIndex) {
            updateCommitIndex(n); //we've accepted a value on a majority of nodes, time to commit it
        }
    }

    /*Our election timeout has occurred, time to start a new election. Follow the specifications in the raft paper on how
    * to construct the requestVote message*/
    private void startNewElection() {
        Logger.info(String.format("Election timeout occurred, timeout value for %s is %d", nodeName, heartBeatTimeoutValue));
        currentTerm++;
        votedFor = nodeName;
        int lastLogIndex = 0;
        int lastLogTerm = 0;

        Entry lastEntry = getLastEntry();
        if (lastEntry != null) {
            lastLogIndex = lastEntry.getIndex(); //get these values for the creation of the requestVoteMessage
            lastLogTerm = lastEntry.getTerm();
        }

        RequestVoteMessageBuilder builder = new RequestVoteMessageBuilder();
        ZMQ.Socket sendingSocket = brokerManager.getElectionStartSocket();

        //construct request
        for (String peer : brokerManager.getPeers()) {
            RequestVoteMessage rvm = builder
                    .setTerm(currentTerm)
                    .setCandidateId(nodeName)
                    .setDestination(peer)
                    .setLastLogIndex(lastLogIndex)
                    .setLastLogTerm(lastLogTerm)
                    .setSource(this.nodeName)
                    .createRequestVoteMessage();
            brokerManager.sendInterThread(rvm.serialize(this.gson), sendingSocket);
        }
        sendingSocket.close();
        this.voteResponses = new HashMap<>();
        voteResponses.put(this.nodeName, true); //note that you voted for yourself in the election
        restartElectionTimeout();
    }

    //returns last entry or null if log is empty
    private Entry getLastEntry() {
        return log.isEmpty() ? null : log.get(log.size() - 1);
    }

    /*Transition to new roles. Always flush get commands in flight -- they don't maintain state and cannot be consistently
    * served during a leader election. */
    void transitionTo(Role role) {
        flushGetCommandsInFlight();
        switch (role) {
            case FOLLOWER:
                cancelHeartbeatTimeout(); //no longer a leader, no need to send heartBeats
                if (connected && this.leader == null) {
                    restartElectionTimeout(); //we need to receive appendEntries, restart electon timeout.
                }
                this.role = role;
                break;

            case CANDIDATE:
                Logger.info(String.format("%s from %s to %s", nodeName, this.role, role));
                if (this.role == Role.LEADER) {
                    Logger.error("Error, invalid state transition to CANDIDATE");
                    System.exit(0);
                }
                this.role = role;
                this.votedFor = null;
                this.leader = null; //--lessen window when sets can be not responded to.
                startNewElection(); //restart election timeout, send new votes
                break;

            case LEADER:
                //reset leader state as presented in raft.
                Logger.info(String.format("%s from %s to %s", nodeName, this.role, role));
                Logger.info("new leader has log" + log.toString());
                if (this.role != Role.CANDIDATE)
                    Logger.warning(nodeName + " ,state transition to LEADER from FOLLOWER");
                this.role = role;
                cancelElectionTimeout();
                // resetting the nextIndex and matchIndex map
                for (String peer : brokerManager.getPeers()) {
                    matchIndex.put(peer, 0);
                    nextIndex.put(peer, log.size());
                }

                // appending no-op to log
                Entry noop = new Entry(false, "", "", currentTerm, log.size(), 0, true);
                log.add(noop);
                if (connected)
                    restartHeartBeatTimeout();

                break;
        }
    }

    /*if node has any set commands waiting on a response that match the requestId of overwritten entries in the log,
    send back fail notices.*/
    private void failOverwrittenLogEntries(List<Entry> entriesToFail) {
        for (Entry entry : entriesToFail) {
            if (!entry.isNoop() && commandsInFlight.containsKey(entry.getRequestId())) {
                ErrorMessage em = new ErrorMessage(MessageType.SET_RESPONSE, null, entry.getRequestId(), this.nodeName,
                        String.format("Cannot process request (%s = %s) -- rejected by new leader",
                                entry.getKey(), entry.getValue()));
                brokerManager.sendToBroker(em.serialize(gson));

                commandsInFlight.remove(entry.getRequestId());
            }

        }
    }

    /*if node has any getCommands waiting on a response, send back errors -- a leader election is in progress.*/
    private void flushGetCommandsInFlight() {
        for (Map.Entry<Integer, ClientCommand> entry : commandsInFlight.entrySet()) {
            if (entry.getValue().getType() == MessageType.GET) {
                ErrorMessage em = new ErrorMessage(MessageType.GET_RESPONSE, null, entry.getKey(), this.nodeName,
                        "Cannot process request at this time, Leader election in progress");
                brokerManager.sendToBroker(em.serialize(gson));
                commandsInFlight.remove(entry.getKey());
            }

        }
    }

    /* if the entry is to be applied, commit to the file backed database*/
    private void applyEntryToStateMachine(Entry entry) {
        if (entry.isNoop())
            entry.setApplied(true);
        if (!entry.isApplied()) { //have we already applied this?
            store.put(entry.getKey(), entry.getValue());
            entry.setApplied(true);
        }
        db.commit();
    }

    /*For all nodes: persist log entries between commitIndex and newIndex
    * For leaders -- send SET responses for log entries successfully committed. */
    private void updateCommitIndex(int newIndex) {
        //persist changes
        if (newIndex == commitIndex)
            return;
        List<Entry> persistedRequests = new ArrayList<>();
        for (Entry e : log.subList(commitIndex+1, newIndex + 1)) {
            applyEntryToStateMachine(e);
            Logger.trace(e.toString() + " " + nodeName);
            persistedRequests.add(e);
        }

        Logger.trace(log.toString());
        Logger.info(String.format("Applied to state machine %s", nodeName));

        if (this.role == Role.LEADER) {
            Logger.info(String.format("Leader %s committed", nodeName));
            //send set responses if you're the leader
            for (Entry request : persistedRequests) {
                if (!request.isNoop()) {
                    Message m = new Message(MessageType.SET_RESPONSE, null, request.getRequestId(), this.nodeName);
                    JsonObject msgToSend = m.serializeToObject(gson);
                    msgToSend.addProperty("key", request.getKey());
                    msgToSend.addProperty("value", request.getValue());
                    brokerManager.sendToBroker(msgToSend.toString().getBytes(CHARSET));
                    if (commandsInFlight.containsKey(request.getRequestId())) {
                        commandsInFlight.remove(request.getRequestId());
                    }
                }


            }
            Logger.info(String.format("Sent set responses as leader %s", nodeName));
        }
        this.commitIndex = newIndex;
    }

    /*When the leader gets heartbeat responses, it can determine that it is still the leader and safely reply to GET
    * commands in flight. */
    private void updateGetRequests(String peer) {
        ClientCommand cmd;
        String key;


        for (Map.Entry<Integer, ClientCommand> entry : commandsInFlight.entrySet()) {
            cmd = entry.getValue();
            int id = entry.getKey();
            key = cmd.getKey();
            if (cmd.getType() == MessageType.GET) {//we've gotten a leadership response from this peer
                cmd.addResponse(peer);
            }
            if (cmd.getResponsesSize() >= quorum) {
                //return the latest value from the store if we've received responses from a quorum of nodes.
                if (store.containsKey(key)) {
                    Message m = new Message(MessageType.GET_RESPONSE, null, id, this.nodeName);
                    JsonObject getResp = m.serializeToObject(gson);
                    getResp.addProperty("key", key);
                    getResp.addProperty("value", store.get(key));
                    brokerManager.sendToBroker(getResp.toString().getBytes(CHARSET));

                } else {
                    ErrorMessage em = new ErrorMessage(MessageType.GET_RESPONSE, null, id, this.nodeName,
                            String.format("No such key: %s in %s", key, nodeName));
                    Logger.debug(String.format("log size is %d", log.size()));
                    Logger.trace(store.toString() + " " + nodeName);
                    brokerManager.sendToBroker(em.serialize(gson));
                }

                commandsInFlight.remove(id);
            }
        }
    }

    //cancel the election timer, will no longer spawn new thread when timer runs out
    private void cancelElectionTimeout() {
        if (electionTimeout != null)
            electionTimeout.cancel(true);
    }

    //spawn new schedule for election timeouts that will make a new thread when the timer runs out
    private void startElectionTimeout() {
        this.electionTimeout =
                this.executorService.scheduleAtFixedRate(new ElectionTimeoutHandler(this),
                        heartBeatTimeoutValue,
                        heartBeatTimeoutValue,
                        TimeUnit.MILLISECONDS);
        Logger.debug(String.format("Started Election Timeout, Election timeout value for %s is %d", nodeName, heartBeatTimeoutValue));


    }

    //restart the election timeout, received heartbeat from leader
    private void restartElectionTimeout() {
        cancelElectionTimeout();
        heartBeatTimeoutValue = ThreadLocalRandom.current().nextInt(electionLowerBound, electionUpperBound);
        this.electionTimeout =
                this.executorService.scheduleAtFixedRate(new ElectionTimeoutHandler(this),
                        heartBeatTimeoutValue,
                        heartBeatTimeoutValue,
                        TimeUnit.MILLISECONDS);
        Logger.debug(String.format("Restarted Election Timeout, Election timeout value for %s is %d", nodeName, heartBeatTimeoutValue));

    }

    //begin sending heartbeats, then schedule them for each heartbeat interval
    private void restartHeartBeatTimeout() {
        cancelHeartbeatTimeout();
        this.heartBeatSend =
                this.executorService.scheduleAtFixedRate(new HeartbeatSender(this, brokerManager),
                        0,
                        HEARTBEAT_INTERVAL,
                        TimeUnit.MILLISECONDS);
    }

    private void cancelHeartbeatTimeout() {
        if (this.heartBeatSend != null) {
            this.heartBeatSend.cancel(true);
        }
    }

    //the three different states a node can have
    enum Role {FOLLOWER, CANDIDATE, LEADER}


}
