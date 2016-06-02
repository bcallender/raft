# raft -- raft -- raft your boat, gently down the consensus stream
Brandon Callender
bmcallender@uchicago.edu
Tasnim Rahman
rahmant@uchicago.edu
Anthony Crespo
acrespo@uchicago.edu

To run: 

* cd impl
* chmod a+x install-mvn.sh
* ./install-mvn.sh
* source ~/.bashrc
* mvn install
* cd target
* run chistributed




# Work Division -- Initial split:
Brandon: ZMQ, requestVote, requestVote Response, Stable storage database management, JSON/message handling scaffolding
Anthony: AppendEntries, AppendEntriesResponse, log/commit logic/management
Tasnim: State Transition management, client GET/SET state data management

# but then...
ALL: Debugging, Refactoring/Fixing major architectural flaws that became evident. 

# Caveats

* Occasionally, the -- --force-leader and -- --force role commands will not result in the selected node staying the leader -- another node might time out waiting
on a heartbeat from the leader node if it is spawned by chistributed at an awkward time window.


* There is a narrow window in which if a leader is partitioned/failed after committing a value but before telling the other nodes its been committed, when the new leader is elected, it may send out a duplicate set response -- this doesn't effect safety, it will never fail a commit a leader has already said was committed, but is technically a flaw