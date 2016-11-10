PSync - A peer to peer sync tool for challenged networks
=========================================================

![Travis Build](https://travis-ci.org/ghoshbishakh/psync-pc.svg?branch=master)

Seamless  data  synchronization  among  peer  entities
is  a  problem  for  any  challenged  network  scenarios  where  in-
frastructural  supports  for  synchronization  are  not  available  or
insufficient.  Such  a  network  follows  a  delay/disruption  tolerant
approach,  in  which  connection  or  contact  time  among  devices
are   intermittent   and   short-lived,   agents   may   use   devices   of
heterogeneous  nature  with  different  communication  and  pro-
cessing  powers,  and  the  communication  channel  may  have  high
loss  rate  that  impacts  the  application  layer  file  synchronization
protocols.  The  bundle  protocol  specifications  for  delay  tolerant
networks   only   describe   the   semantics   for   data   transmission
between  two  distant  devices,  and  do  not  specify  how  a  peer-to-
peer (P2P) file synchronization protocol can cater seamless data
exchange among multiple heterogeneous communication devices.
Here we propose a new P2P sync, called
pSync
, on the top
of the bundle protocol which precisely takes care of prioritized file
Sync with role based transfer applicable for challenged networks.
Our  testbed  experiments  conducted  with  information  dropbox,
ground  and  aerial  data  mule  suggest  that
pSync
is  scalable  and
clearly outperforms BitTorrent Sync in delivering even
30%
more
priority  packets  in  certain  test  cases.
