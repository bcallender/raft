import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.json.JSONException;
import org.json.JSONObject;
import org.zeromq.ZMsg;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.*;

/**
 * Created by techbar on 5/25/16.
 */
public class Node implements Serializable {

    public static final Charset CHARSET = Charset.defaultCharset();
    private static final int HEARTBEAT_INTERVAL = 1500;
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


    //persistent
    private int currentTerm;
    private String votedFor;

    //requestVote State
    private HashMap<String, Boolean> voteResponses;

    public Node(String nodeName, BrokerManager brokerManager) {
        this.nodeName = nodeName;
        this.store = new HashMap<>();
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
        this.heartBeatTimeoutValue = ThreadLocalRandom.current().nextInt(10000, 30000);
        this.gson = new Gson();

        Entry e = new Entry(true, null, null, 0, 0, 0); // no op
        this.log.add(e);

        transitionTo(Role.FOLLOWER);
    }

    private void startElectionTimeout() {
        this.electionTimeout =
                this.executorService.scheduleAtFixedRate(new ElectionTimeoutHandler(this),
                        4000,
                        heartBeatTimeoutValue,
                        TimeUnit.MILLISECONDS);
        Logger.debug(String.format("Started Election Timeout, Election timeout value for %s is %d", nodeName, heartBeatTimeoutValue));


    }

    private void restartElectionTimeout() {
        if (this.electionTimeout != null) {
            this.electionTimeout.cancel(true);
        }
        this.electionTimeout =
                this.executorService.scheduleAtFixedRate(new ElectionTimeoutHandler(this),
                        heartBeatTimeoutValue,
                        heartBeatTimeoutValue,
                        TimeUnit.MILLISECONDS);
        Logger.debug(String.format("Restarted Election Timeout, Election timeout value for %s is %d", nodeName, heartBeatTimeoutValue));

    }


    private void restartHeartBeatTimeout() {
        if (this.heartBeatSend != null) {
            this.heartBeatSend.cancel(true);
        }
        this.heartBeatSend =
                this.executorService.scheduleAtFixedRate(new HeartbeatSender(this),
                        HEARTBEAT_INTERVAL,
                        HEARTBEAT_INTERVAL,
                        TimeUnit.MILLISECONDS);
    }

    public void handleMessage(ZMsg message) {
        assert (message.size() == 3);
        JSONObject msg = new JSONObject(message.getLast().toString());
        MessageType type = MessageType.parse(msg.getString("type"));

        switch (type) {
            case APPEND_ENTRIES:
                handleAppendEntries(msg);
                break;
            case APPEND_ENTRIES_RESPONSE:
                handleAppendEntriesResponse(msg);
                break;
            case REQUEST_FORWARD:
                handleForwardRequest(msg);
                break;
            case REQUEST_FORWARD_RESPONSE:
                handleForwardRequestResponse(msg);
                break;
            case REQUEST_VOTE:
                handleRequestVote(msg);
                break;
            case REQUEST_VOTE_RESPONSE:
                handleRequestVoteResponse(msg);
                break;
            case GET:
                handleGetMessage(msg);
                break;
            case SET:
                handleSetMessage(msg);
                break;
            case HELLO:
                if (!connected) {
                    connected = true;
                    JSONObject hr = new JSONObject(String.format("{'type': 'helloResponse', 'source': %s}", nodeName));
                    brokerManager.sendToBroker(hr.toString().getBytes(CHARSET));
                    Logger.info("BrokerManager Running");
                    startElectionTimeout();
                }
            case UNKNOWN:
        }
    }

    //updates state to newTerm, does nothing if newTerm is stale
    private void updateTerm(int newTerm) {
        if (newTerm > currentTerm) {
            currentTerm = newTerm;
            votedFor = null;
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
                Entry entry = new Entry(false, key, value, currentTerm, log.size(), id);
                log.add(entry);

            } else {
                ErrorMessage em = new ErrorMessage(MessageType.SET_RESPONSE, null, msg.getInt("id"), this.nodeName,
                        String.format("SET commands may only be sent to leader node. I think the current leader is %s", this.leader));
                brokerManager.sendToBroker(em.serialize(gson));
            }
        } catch (JSONException e) {
            Logger.warning(String.format("Invalid set received at %s", this.nodeName));
            ErrorMessage em = new ErrorMessage(MessageType.SET_RESPONSE, null, msg.getInt("id"), this.nodeName,
                    String.format("You sent me invalid JSON: %s", msg.toString()));
            brokerManager.sendToBroker(em.serialize(gson));
        }


    }

    private void handleForwardRequestResponse(JSONObject msg) {

        int id = msg.getInt("id");
        //is this reply stale? (my inFlight table has been flushed since i sent this)
        if (commandsInFlight.containsKey(id)) {
            //is this an error?
            commandsInFlight.remove(id);
            if (msg.has("error")) {
                ErrorMessage response = ErrorMessage.deserialize(msg.toString(), gson);
                ErrorMessage reply = new ErrorMessage(MessageType.GET_RESPONSE, null, id, this.nodeName, response.getError());
                brokerManager.sendToBroker(reply.serialize(gson));
            } else { //message has the data we need!
                String key = msg.getString("key");
                Message m = new Message(MessageType.GET_RESPONSE, null, id, this.nodeName);
                JsonObject toSend = m.serializeToObject(gson);
                toSend.addProperty("key", key);
                toSend.addProperty("value", msg.getString("value"));
                brokerManager.sendToBroker(toSend.toString().getBytes(CHARSET));
            }
        }

        //drop message if stale
    }

    private void handleForwardRequest(JSONObject msg) {
        String key = msg.getString("key");
        int id = msg.getInt("id");
        String source = msg.getString("source");
        //am i the leader?
        if (this.role == Role.LEADER) {
            if (store.containsKey(key)) {
                Message m = new Message(MessageType.REQUEST_FORWARD_RESPONSE, source, id, this.nodeName);
                JsonObject reply = m.serializeToObject(gson);
                reply.addProperty("key", key);
                reply.addProperty("value", store.get(key));
                brokerManager.sendToBroker(reply.toString().getBytes(CHARSET));
            } else {
                ErrorMessage em = new ErrorMessage(MessageType.REQUEST_FORWARD_RESPONSE, source, id, this.nodeName,
                        String.format("No such key: %s", key));
                brokerManager.sendToBroker(em.serialize(gson));
            }
        } else {
            ErrorMessage em = new ErrorMessage(MessageType.REQUEST_FORWARD_RESPONSE, source, id, this.nodeName,
                    "Cannot identify leader -- no referenced at follower perceived leader");
            brokerManager.sendToBroker(em.serialize(gson));
        }

    }

    private void handleGetMessage(JSONObject msg) {
        try {
            String key = msg.getString("key");
            int id = msg.getInt("id");
            ClientCommand command = new ClientCommand(MessageType.GET, key, null);
            commandsInFlight.put(id, command);
            //am I the leader?
            if (this.role == Role.LEADER) {
                //return the latest value from the store TODO:check if still leader?


                if (store.containsKey(key)) {

                    Message m = new Message(MessageType.GET_RESPONSE, null, id, this.nodeName);
                    JsonObject getResp = m.serializeToObject(gson);
                    getResp.addProperty("key", key);
                    getResp.addProperty("value", store.get(key));
                    brokerManager.sendToBroker(getResp.toString().getBytes(CHARSET));

                } else {
                    ErrorMessage em = new ErrorMessage(MessageType.GET_RESPONSE, null, id, this.nodeName,
                            String.format("No such key: %s", key));
                    brokerManager.sendToBroker(em.serialize(gson));
                }

                commandsInFlight.remove(id);


            } else { //i'm not the leader, I need to get the value from the leader
                //do we know who the leader is?
                if (leader != null) {
                    Message m = new Message(MessageType.REQUEST_FORWARD, leader, id, this.nodeName);
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
        if (voteTerm > currentTerm || (voteTerm == currentTerm && (votedFor == null/* || votedFor.equals(candidateId)*/))) {
            //TODO voteTerm & currentTerm compared twice
            updateTerm(voteTerm);
            int logTerm = m.getLastLogTerm();
            int logIndex = m.getLastLogIndex();
            Entry lastEntry = getLastEntry();
            //if log is empty then the other is vacuously up to date, otherwise compare them for recency
            if (lastEntry == null || (lastEntry.moreRecentThan(logTerm, logIndex))) {
                votedFor = candidateId;
                success = true;
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
            int quorum = (brokerManager.getPeers().size() + 1) / 2;
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
                    Logger.info(String.format("Node %s did not receive a quorum of votes. Split Vote..", this.nodeName));
                    transitionTo(Role.CANDIDATE);
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
            int index = m.getPrevLogIndex();
            Entry myEntry = getLastEntry();
            if (m.getEntries().isEmpty() || myEntry == null || myEntry.getTerm() == m.getPrevLogTerm()) {
                success = true;
                if (!m.getEntries().isEmpty()) {
                    List<Entry> entries = m.getEntries();
                    if (!log.isEmpty()) {
                        log = log.subList(0, index + 1);
                    }
                    log.addAll(entries);
                }
                int leaderCommit = m.getLeaderCommit();
                if (leaderCommit > commitIndex)
                    updateCommitIndex(Math.min(leaderCommit, log.size() - 1));
                    //TODO persist commits
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
                nextIndex.put(m.source, next);
            }
        } else {
            //drop message if not leader
            Logger.info(String.format("%s received extraneous append entries response from %s:%s", this.nodeName,
                    m.source, m.success));
        }

    }

    private void startNewElection() {
        Logger.debug(String.format("Election timeout occurred, timeout value for %s is %d", nodeName, heartBeatTimeoutValue));
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
                this.role = role;
                if (heartBeatSend != null)
                    heartBeatSend.cancel(true);
                if (connected) {
                    restartElectionTimeout();
                }
                break;

            case CANDIDATE:
                if (this.role == Role.LEADER) {
                    Logger.error("Error, invalid state transition to CANDIDATE");
                    System.exit(0);
                }
                this.role = role;
                this.votedFor = null;
                startNewElection();
                break;

            case LEADER:
                if (this.role != Role.CANDIDATE)
                    Logger.error("Error, invalid state transition to LEADER");
                this.role = role;
                electionTimeout.cancel(true);
                // resetting the nextIndex and matchIndex map
                for (String peer : brokerManager.getPeers()) {
                    matchIndex.put(peer, 0);
                    nextIndex.put(peer, log.size());
                }

                restartHeartBeatTimeout();

                break;
        }
    }

    public void sendHeartbeats() {
        int n;
        for (n = commitIndex+1; n < log.size(); n++) {
            if (log.get(n).getTerm() == currentTerm)
                    break;
        }
        int acceptedCount = 1;
        for (String peer : brokerManager.getPeers()) {
            if (matchIndex.get(peer) >= n) {
                Logger.warning("GOT HERE?");
                acceptedCount++;
            }
            AppendEntriesMessageBuilder aemb = new AppendEntriesMessageBuilder()
                    .setTerm(currentTerm)
                    .setDestination(peer)
                    .setLeaderCommit(commitIndex)
                    .setSource(this.nodeName)
                    .setLeaderId(this.nodeName);
            if (!log.isEmpty())
                aemb.setEntries(log.subList(nextIndex.get(peer), log.size()));
            AppendEntriesMessage aem = aemb.createAppendEntriesMessage();
            brokerManager.sendToBroker(aem.serialize(gson));
        }
        if (acceptedCount > (brokerManager.getPeers().size() + 1) / 2 && log.get(n).getTerm() == currentTerm) {
            Logger.warning("commit index HERE?");
            updateCommitIndex(n);
            //TODO persist
        }
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
        if (!entry.applied) { //have we already applied this? TODO: necessary?
            store.put(entry.key, entry.value);
            entry.applied = true;
        }
    }

    private void updateCommitIndex(int newIndex) {
        //persist changes
        List<Integer> persistedRequests = new ArrayList<>();
        for (Entry e : log.subList(commitIndex, newIndex + 1)) {
            applyEntryToStateMachine(e);
            persistedRequests.add(e.requestId);
        }
        //TODO: actually persist to disk

        if (this.role == Role.LEADER) {
            //send set responses if you're the leader
            for (Integer requestId : persistedRequests) {
                Message m = new Message(MessageType.SET_RESPONSE, null, requestId, this.nodeName);
                JsonObject msgToSend = m.serializeToObject(gson);
                msgToSend.addProperty("key", commandsInFlight.get(requestId).getKey());
                msgToSend.addProperty("value", commandsInFlight.get(requestId).getValue());
                brokerManager.sendToBroker(msgToSend.toString().getBytes(CHARSET));
                commandsInFlight.remove(requestId);
            }
        }
    }



    public enum Role {FOLLOWER, CANDIDATE, LEADER}
}
