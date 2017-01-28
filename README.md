pSync - A peer to peer sync tool for challenged networks
=========================================================

[![Build Status](https://travis-ci.org/ItsForkIT/psync-pc.svg?branch=master)](https://travis-ci.org/ItsForkIT/psync-pc)

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


# [Download JAR](https://github.com/ghoshbishakh/psync-pc/raw/master/distributables/psync-pc-jar-with-dependencies.jar)

## License

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0


## For any research work using this tool please cite the following:

1. [On design and implementation of a scalable and reliable Sync system for delay tolerant challenged networks](http://ieeexplore.ieee.org/abstract/document/7439949/)

