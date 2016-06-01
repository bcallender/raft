# raft -- raft -- raft your boat, gently down the consensus stream

To run: 

```* cd ~/impl
* chmod a+x install-mvn.sh
* ./install-mvn.sh
* source ~/.bashrc
* mvn install
* cd target
* run chistributed
```



# Class Summary:

The BrokerManager handles all inter-thread and node<-->chistributed communication. As in the sample code, there are two sockets for communicating with chistributed, a subSock that receives messages from chistributed (eg SET, GET and others) and a reqSock that sends messages to chistributed (and thus, other nodes). Unlike in the sample code however, the reqSock is implemented as type DEALER rather than REQ. The election/heartbeat threads do not send messages directly but instead pass the messages back to the main thread to be forwarded at the next poll. Benefits and drawbacks of this approach can be found in the paper.

The node class implements all logic needed at the node level in the Raft paper. A dispatcher in handleMessage sends messages to handler functions, which are defined for all valid message types. Uses a scheduled threadPool to handle sending heartbeats and timing out on election timeouts.

AppendEntriesMessage, RequestVoteMessage, RPCMessageResponse, and ErrorMessages are subclasses of the Message class. They are serializable/de-serializable to/from JSON using GSON.

Entry in the log of a raft node. Contains two special fields (noop and applied) which are used in our implmentation of
leader elections and commiting entires to stable storage respectively. Also contains the requestId of the client command
to make sure that responses are always sent to requests, regardless of leader elections.

Logger is a basic static logger.

ElectionTimeoutHandler/Heartbeat Sender are Runnables that are spawned by the election timeout scheduledFuture and heartbeatInterval scheduledFeature to start a new election or send heartbeats respectively.

Control Flow:

Messages are captured by the brokerManager and sent to the Node for processsing. The Node handles all message processing logic and contacts the message brokers to send messages between nodes and to chistributed. Nodes can spawn threads that communicate back to the master thread (using ZMQ PAIR sockets) when they need to use the main socket to send messages to chistributed.