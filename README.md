# raft -- raft -- raft your boat, gently down the consensus stream

To run: 

* cd impl
* chmod a+x install-mvn.sh
* ./install-mvn.sh
* source ~/.bashrc
* mvn install
* cd target
* run chistributed



# Class Summary:

The BrokerManager handles all inter-thread and node<-->chistributed communication. As in the sample code, there are two sockets for communicating with chistributed, a subSock that receives messages from chistributed (eg SET, GET and others) and a reqSock that sends messages to chistributed (and thus, other nodes). Unlike in the sample code however, the reqSock is implemented as type DEALER rather than REQ. The election/heartbeat threads do not send messages directly but instead pass the messages back to the main thread to be forwarded at the next poll. Benefits and drawbacks of this approach can be found in the paper.

The node class implements all logic needed at the node level in the Raft paper. 
A dispatcher in handleMessage sends messages to handler functions, which are defined 
for all valid message types. It uses a scheduled threadPool to handle sending 
heartbeats and timing out on election timeouts. It tracks state across several data structures --
a file backed MapDB to persist key-value pairs, a ConcurrentHashMap commandsInFlight 
that maintains information on SET/GET commands in flight, a Log (List) and the variables that are 
standard in the raft paper. 

AppendEntriesMessage, RequestVoteMessage, RPCMessageResponse, and ErrorMessages are subclasses of the Message class. They are serializable/de-serializable to/from JSON using GSON.
Their specific variables, when they differ from the the raft reference, are documented.

Entry in the log of a raft node. Contains two special fields (noop and applied) which are used in our implmentation of
leader elections and commiting entires to stable storage respectively. Also contains the requestId of the client command
to make sure that responses are always sent to requests, regardless of leader elections.

Logger is a basic static logger -- was initially going to have more customization to differentiate it from java.utils.Logging but time restraints.

ElectionTimeoutHandler/Heartbeat Sender are Runnables that are spawned by the election timeout scheduledFuture and heartbeatInterval scheduledFeature to start a new election or send heartbeats respectively.

# Control Flow:

Messages are captured by the brokerManager and sent to the Node for processsing. The Node handles all message processing logic and contacts the message brokers to send messages between nodes and to chistributed. Nodes can spawn threads that communicate back to the master thread (using ZMQ PAIR sockets) when they need to use the main socket to send messages to chistributed.

Libraries Used:
org.json : JSON serialization library primarily used because it makes it very easy to serialze a POJO into an intermediate object we can then add properties to.
This is much harder in gson.

com.gson: JSON serialization library used because it is very good at serializing and deserializing JSON to and from Java Classes but isn't particularly flexible
with intermediate object/custom objects we didn't want to define a class for.

com.JCommander: command line argument parsing library for Java

org.mapDB -- a simple file-backed store that implements the map interface, making it easy to wire up to the application. it's overkill, but we disable transactions
and just commit on every write to avoid inconsistency.

org.jeromq -- library to communicate with zeromq


# Work Division -- Initial split:
Brandon: ZMQ, requestVote, requestVote Response, Stable storage database management, JSON/message handling scaffolding
Anthony: AppendEntries, AppendEntriesResponse, log/commit logic/management
Tasnim: State Transition management, client GET/SET state data management

# but then...
ALL: Debugging, Refactoring/Fixing major architectural flaws that became evident. 

# Caveats

* Occasionally, the -- --force-leader and -- --force role commands will not result in the selected node staying the leader -- another node might time out waiting
on a heartbeat from the leader node if it is spawned by chistributed at an awkward time window.


* There is a narrow window in which if a leader is partitioned/failed after committing a value but before telling the other nodes its been committed, when the new leader
is elected, it may send out a duplicate set response -- this doesn't effect safety, it will never fail a commit a leader has already said was committed, but is 
technically a flaw