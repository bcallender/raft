To run: 

cd ~/impl
chmod a+x install-mvn.sh
./install-mvn.sh
source ~/.bashrc
mvn install
cd target
run chistributed

The BrokerManager handles all inter-thread and node<-->chistributed communication. As in the sample code, there are two
sockets for communicating with chistributed, a subSock that receives messages from chistributed (eg SET, GET and others)
and a reqSock that sends messages to chistributed (and thus, other nodes). Unlike in the sample code however, the reqSock
is implemented as type DEALER rather than REQ. The election/heartbeat threads do not send messages directly
but instead pass the messages back to the main thread to be forwarded at the next poll. Benefits and drawbacks of this approach can
be found in the paper.

The node class implements all logic needed at the node level in the Raft paper. A dispatcher in handleMessage
sends messages to handler functions, which are defined for all valid messagee types. Uses a scheduled threadPool to
handle sending heartbeats and timing out on election timeouts.