import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.json.JSONException;
import org.json.JSONObject;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.zeromq.ZMsg;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.*;

/**
 * Created by techbar on 5/25/16.
 */
public class Node implements Serializable {

    public static final Charset CHARSET = Charset.defaultCharset();
    private static final int HEARTBEAT_INTERVAL = 250;
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    //volatile on all
    int commitIndex;
    Role role;
    String leader;
    //volatile on master
    Map<String, Integer> nextIndex;
    Map<String, Integer> matchIndex;
    List<Entry> log;
    private ScheduledFuture<?> heartBeatSend;
    private ScheduledFuture<?> electionTimeout;
    private int heartBeatTimeoutValue;
    private Gson gson;
    private Map<Integer, ClientCommand> commandsInFlight;
    private BrokerManager brokerManager;
    private Map<String, String> store;
    private String nodeName;
    private boolean connected;
    private DB db;
    private int quorum;

    //persistent
    private int currentTerm;
    private String votedFor;

    //requestVote State
    private HashMap<String, Boolean> voteResponses;

    public Node(String nodeName, BrokerManager brokerManager, String startRole, String startingLeader) {
        this.nodeName = nodeName;
        this.brokerManager = brokerManager;
        this.connected = false;
        this.currentTerm = 0;
        this.votedFor = null;
        this.log = new ArrayList<>();
        this.commitIndex = 0;
        this.role = Role.FOLLOWER;
        this.nextIndex = new TreeMap<>();
        this.matchIndex = new TreeMap<>();
        this.commandsInFlight = new ConcurrentHashMap<>();
        this.voteResponses = new HashMap<>();
        this.heartBeatTimeoutValue = ThreadLocalRandom.current().nextInt(1000, 2500);
        this.gson = new Gson();
        this.quorum = (brokerManager.getPeers().size() + 1) / 2;

        //file backed db.
        try {
            File dbFile = File.createTempFile(this.nodeName, ".tmpDB");
            this.db = DBMaker.fileDB(dbFile)
                    .fileMmapEnableIfSupported()
                    .closeOnJvmShutdown()
                    .make();
            this.store = db.hashMap(this.nodeName);
            //this.store = new HashMap<>();
        } catch (IOException e) {
            Logger.error("IO error creating db file");
            Logger.error(e.getMessage());
            System.exit(-1);
        }


        Entry e = new Entry(true, null, null, 0, 0, 0, true); // no op
        this.log.add(e);

        if (startingLeader != null) {
            this.leader = startingLeader;
        }

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

    public String getNodeName() {
        return nodeName;
    }

    private void startElectionTimeout() {
        this.electionTimeout =
                this.executorService.scheduleAtFixedRate(new ElectionTimeoutHandler(this),
                        heartBeatTimeoutValue,
                        heartBeatTimeoutValue,
                        TimeUnit.MILLISECONDS);
        Logger.debug(String.format("Started Election Timeout, Election timeout value for %s is %d", nodeName, heartBeatTimeoutValue));


    }

    private void restartElectionTimeout() {
        cancelElectionTimeout();
        heartBeatTimeoutValue = ThreadLocalRandom.current().nextInt(700, 2000);
        this.electionTimeout =
                this.executorService.scheduleAtFixedRate(new ElectionTimeoutHandler(this),
                        heartBeatTimeoutValue,
                        heartBeatTimeoutValue,
                        TimeUnit.MILLISECONDS);
        Logger.debug(String.format("Restarted Election Timeout, Election timeout value for %s is %d", nodeName, heartBeatTimeoutValue));

    }


    private void restartHeartBeatTimeout() {
        cancelHeartbeatTimeout();
        this.heartBeatSend =
                this.executorService.scheduleAtFixedRate(new HeartbeatSender(this),
                        0, //HEARTBEAT_INTERVAL
                        HEARTBEAT_INTERVAL,
                        TimeUnit.MILLISECONDS);
    }

    private void cancelHeartbeatTimeout() {
        if (this.heartBeatSend != null) {
            this.heartBeatSend.cancel(true);
        }
    }

    public void handleMessage(ZMsg message) {
        assert (message.size() == 3);
        JSONObject msg = new JSONObject(message.getLast().toString());
        MessageType type = MessageType.parse(msg.getString("type"));

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
                    JSONObject hr = new JSONObject(String.format("{'type': 'helloResponse', 'source': %s}", nodeName));
                    brokerManager.sendToBroker(hr.toString().getBytes(CHARSET));
                    Logger.info(this.nodeName + " BrokerManager Running");
                    if (this.role == Role.LEADER) //forced to be leader
                        restartHeartBeatTimeout();
                    else
                        startElectionTimeout();
                }
            case UNKNOWN:
        }
    }

    //updates state to newTerm, does nothing if newTerm is stale
    private void updateTerm(int newTerm) {
        if (newTerm > currentTerm) {
            votedFor = null;
            currentTerm = newTerm;
            transitionTo(Role.FOLLOWER);
        }
    }

    private void handleSetMessage(JSONObject msg) {
        try {
            String key = msg.getString("key");
            String value = msg.getString("value");
            int id = msg.getInt("id");

            if (this.role == Role.LEADER) { //TODO: log replication
                commandsInFlight.put(msg.getInt("id"), new ClientCommand(MessageType.SET, key, value));
                Entry entry = new Entry(false, key, value, currentTerm, log.size(), id, false);
                log.add(entry);

            } else {
                if (leader != null) {
                    Message m = new Message(MessageType.SET_REQUEST_FORWARD, leader, id, this.nodeName);
                    JsonObject fwd = m.serializeToObject(gson);
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
        } catch (JSONException e) {
            Logger.warning(String.format("Invalid set received at %s", this.nodeName));
            ErrorMessage em = new ErrorMessage(MessageType.SET_RESPONSE, null, msg.getInt("id"), this.nodeName,
                    String.format("You sent me invalid JSON: %s", msg.toString()));
            brokerManager.sendToBroker(em.serialize(gson));
        }


    }

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

    private void handleRequestVote(JSONObject msg) {
        RequestVoteMessage m = RequestVoteMessage.deserialize(msg.toString().getBytes(CHARSET), gson);
        int voteTerm = m.getTerm();
        String candidateId = m.getCandidateId();
        boolean success = false;
        //if the message is a later term than ours or we have yet to vote
        if (voteTerm > currentTerm || (voteTerm == currentTerm && (votedFor == null || votedFor.equals(candidateId)))) {
            //TODO voteTerm & currentTerm compared twice
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
        response.setDestination(m.source);
        response.setSource(nodeName);
        response.setTerm(voteTerm);
        response.setType(MessageType.REQUEST_VOTE_RESPONSE);
        response.setSuccess(success);
        brokerManager.sendToBroker(response.createRPCMessageResponse().serialize(gson));
    }

    private void handleRequestVoteResponse(JSONObject msg) {

        RPCMessageResponse response = RPCMessageResponse.deserialize(msg.toString().getBytes(CHARSET),
                gson);
        if (this.role == Role.CANDIDATE) {
            this.voteResponses.put(response.source, response.success);
            int numYeas = 0;
            int numNays = 0;
            if (voteResponses.size() > quorum) { //we have enough votes to check for quorum
                for (Boolean vote : voteResponses.values()) {
                    if (vote)
                        numYeas++;
                    else
                        numNays++;
                }
                if (numYeas > quorum) { //success //
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
                    response.source, response.success));
        }



    }

    private void handleAppendEntries(JSONObject msg) {
        AppendEntriesMessage m = AppendEntriesMessage.deserialize(msg.toString().getBytes(CHARSET), gson);
        boolean success = false;
        if (currentTerm <= m.getTerm()) { //TODO: correct?
            this.leader = m.source;
            updateTerm(m.getTerm());
            int nextIndex = m.getPrevLogIndex();
            if (nextIndex < log.size()) {
                //Logger.info(String.format("%s, leader thinks next is %d, log size is %d", nodeName, nextIndex, log.size()));

                Entry myEntry = log.get(nextIndex);
                if (myEntry.getTerm() == m.getPrevLogTerm()) {
                    success = true;
                    List<Entry> entries = m.getEntries();
                    if (!log.isEmpty()) {
                        log = log.subList(0, nextIndex + 1);
                    }
                    log.addAll(entries);

                    int leaderCommit = m.getLeaderCommit();
                    if (leaderCommit > commitIndex) {
                        updateCommitIndex(Math.min(leaderCommit, log.size() - 1));
                    }
                }
            }
            restartElectionTimeout();
        }
        //send result
        RPCMessageResponseBuilder response = new RPCMessageResponseBuilder();
        response.setDestination(m.source);
        response.setSource(nodeName);
        response.setTerm(currentTerm);
        response.setType(MessageType.APPEND_ENTRIES_RESPONSE);
        response.setSuccess(success);
        response.setLogIndex(log.size() - 1);
        brokerManager.sendToBroker(response.createRPCMessageResponse().serialize(gson));
    }


    public void handleAppendEntriesResponse(JSONObject msg) {
        RPCMessageResponse m = RPCMessageResponse.deserialize(msg.toString().getBytes(Charset.defaultCharset()), gson);
        if (role == Role.LEADER) {
            if (m.success) { //on success update recorded state for that node
                matchIndex.put(m.source, m.logIndex);
                nextIndex.put(m.source, m.logIndex+1);
            } else { //on failure decrement relevant nextIndex for next send
                Integer next = nextIndex.get(m.source) -1;
                if (next < 0) {
                    Logger.error(String.format("%s has a negative nextIndex", m.source));
                }
                nextIndex.put(m.source, next);
            }

            updateGetRequests(m.source);
        } else {
            //drop message if not leader
            Logger.info(String.format("%s received extraneous append entries response from %s:%s", this.nodeName,
                    m.source, m.success));
        }

    }

    public void sendHeartbeats() {
        int n;
        //Logger.info("started hb");
        for (n = commitIndex+1; n < log.size(); n++) {
            if (log.get(n).getTerm() == currentTerm)
                break;
        }
        int acceptedCount = 1;
        for (String peer : brokerManager.getPeers()) {
            //Logger.info("sending hb to " + peer);
            if (matchIndex.get(peer) >= n) {
                acceptedCount++;
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
                    .setPrevLogTerm(log.get(nextIndex.get(peer) - 1).term);
            //Logger.info("Built message for " + peer);
            AppendEntriesMessage aem = aemb.createAppendEntriesMessage();
            brokerManager.sendToBroker(aem.serialize(gson));
        }

        if (acceptedCount > (brokerManager.getPeers().size() + 1) / 2 && log.get(n).getTerm() == currentTerm && n > commitIndex) {
            updateCommitIndex(n);
        }
        //Logger.info("ended hb");
    }

    private void startNewElection() {
        Logger.info(String.format("Election timeout occurred, timeout value for %s is %d", nodeName, heartBeatTimeoutValue));
        currentTerm++;
        votedFor = nodeName;
        int lastLogIndex = 0;
        int lastLogTerm = 0;

        Entry lastEntry = getLastEntry();
        if (lastEntry != null) {
            lastLogIndex = lastEntry.index;
            lastLogTerm = lastEntry.term;
        }

        RequestVoteMessage rvm;

        for (String peer : brokerManager.getPeers()) {
            rvm = new RequestVoteMessageBuilder()
                    .setTerm(currentTerm)
                    .setCandidateId(nodeName)
                    .setDestination(peer)
                    .setLastLogIndex(lastLogIndex)
                    .setLastLogTerm(lastLogTerm)
                    .setSource(this.nodeName)
                    .createRequestVoteMessage();
            brokerManager.sendToBroker(rvm.serialize(this.gson));
        }
        this.voteResponses = new HashMap<>();
        voteResponses.put(this.nodeName, true);
        restartElectionTimeout();
    }

    //returns last entry or null if log is empty
    private Entry getLastEntry() {
        return log.isEmpty() ? null : log.get(log.size() - 1);
    }

    public void transitionTo(Role role) {
        flushCommandsInFlight();
        switch (role) {
            case FOLLOWER:
                cancelHeartbeatTimeout();
                if (connected && this.leader == null) {
                    restartElectionTimeout();
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
                startNewElection();
                break;

            case LEADER:
                Logger.info(String.format("%s from %s to %s", nodeName, this.role, role));
                Logger.info("new leader has log" + log.toString());
                if (this.role != Role.CANDIDATE)
                    Logger.warning("state transition to LEADER from FOLLOWER");
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

    private void cancelElectionTimeout() {
        if (electionTimeout != null)
            electionTimeout.cancel(true);
    }

    private void flushCommandsInFlight() {
        for (Map.Entry<Integer, ClientCommand> entry : commandsInFlight.entrySet()) {
            if (entry.getValue().getType() == MessageType.GET) {
                ErrorMessage em = new ErrorMessage(MessageType.GET_RESPONSE, null, entry.getKey(), this.nodeName,
                        "Cannot process request at this time, Leader election in progress");
                brokerManager.sendToBroker(em.serialize(gson));
            } else {
                ErrorMessage em = new ErrorMessage(MessageType.SET_RESPONSE, null, entry.getKey(), this.nodeName,
                        String.format("Cannot process request (%s = %s) at this time, Leader election in progress",
                                entry.getValue().getKey(), entry.getValue().getValue()));
                brokerManager.sendToBroker(em.serialize(gson));
            }
            commandsInFlight.remove(entry.getKey());

        }
    }

    private void applyEntryToStateMachine(Entry entry) {
        if (entry.noop)
            entry.applied = true;
        if (!entry.applied) { //have we already applied this? TODO: necessary?
            store.put(entry.key, entry.value);
            entry.applied = true;
        }
        db.commit();
    }

    private void updateCommitIndex(int newIndex) {
        //persist changes
        if (newIndex == commitIndex)
            return;
        List<Integer> persistedRequests = new ArrayList<>();
        for (Entry e : log.subList(commitIndex+1, newIndex + 1)) {
            applyEntryToStateMachine(e);
            Logger.trace(e.toString() + " " + nodeName);
            persistedRequests.add(e.requestId);
        }

        Logger.trace(log.toString());
        Logger.info(String.format("Applied to state machine %s", nodeName));

        if (this.role == Role.LEADER) {
            Logger.info(String.format("Leader %s committed", nodeName));
            //send set responses if you're the leader
            for (Integer requestId : persistedRequests) {
                if (commandsInFlight.containsKey(requestId)) {
                    ClientCommand clientCommand = commandsInFlight.get(requestId);
                    Message m = new Message(MessageType.SET_RESPONSE, null, requestId, this.nodeName);
                    JsonObject msgToSend = m.serializeToObject(gson);
                    msgToSend.addProperty("key", clientCommand.getKey());
                    msgToSend.addProperty("value", clientCommand.getValue());
                    brokerManager.sendToBroker(msgToSend.toString().getBytes(CHARSET));
                    commandsInFlight.remove(requestId);
                }

            }
            Logger.info(String.format("Sent set responses as leader %s", nodeName));
        }
        this.commitIndex = newIndex;
    }

    private void updateGetRequests(String peer) {
        ClientCommand cmd;
        String key;


        for (Map.Entry<Integer, ClientCommand> entry : commandsInFlight.entrySet()) {
            cmd = entry.getValue();
            int id = entry.getKey();
            key = cmd.getKey();
            if (cmd.getType() == MessageType.GET) {
                cmd.addResponse(peer);
            }
            if (cmd.getResponsesSize() >= quorum) {
                //return the latest value from the store
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


    public enum Role {FOLLOWER, CANDIDATE, LEADER}
}
