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
clearly outperforms [BitTorrent Sync (now Resilio Sync)](https://en.wikipedia.org/wiki/Resilio_Sync) in delivering even
30%
more
priority  packets  in  certain  test  cases.


# [Download JAR](https://github.com/ghoshbishakh/psync-pc/raw/master/distributables/psync-pc-jar-with-dependencies.jar)


# Instructions

1. Create a working folder.

2. Add a subfolder named 'sync' which will contain all files that will be synced.

3. Run the jar:
    ```
    java -jar psync-pc-jar-with-dependencies.jar <PEER_ID> <ABSOLUTE_PATH_TO_WORKING_FOLDER_WITH_TRAILING_SLASH> <priority_method (optional)> <restricted epidemic flag (optional)>
    ```
    
    Priority method:
    * 0: Random
    * 1: Based on Priority
    * 2: Based on Importance (default)
    * 3: KML based Chat priority


    Restricted epidemic flag:
    * true: restricted epidemic enabled (default)
    * false: restricted epidemic disabled

## License

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0


## For any research work using this tool please cite the following:

1. [On design and implementation of a scalable and reliable Sync system for delay tolerant challenged networks](http://ieeexplore.ieee.org/abstract/document/7439949/)

```
@INPROCEEDINGS{7439949,
author={P. S. Paul and B. C. Ghosh and K. De and S. Saha and S. Nandi and S. Saha and I. Bhattacharya and S. Chakraborty},
booktitle={2016 8th International Conference on Communication Systems and Networks (COMSNETS)},
title={On design and implementation of a scalable and reliable Sync system for delay tolerant challenged networks},
year={2016},
pages={1-8},
keywords={delay tolerant networks;peer-to-peer computing;protocols;synchronisation;telecommunication channels;application layer file synchronization protocols;bittorrent sync;bundle protocol specifications;communication channel;delay tolerant challenged networks;information dropbox;peer-to-peer file synchronization protocol;seamless data exchange;seamless data synchronization;Androids;Humanoid robots;Peer-to-peer computing;Portable computers;Ports (Computers);Protocols;Synchronization;DTN;challenged networks;peer-to-peer;sync},
doi={10.1109/COMSNETS.2016.7439949},
month={Jan},}
```

## AUTHORS

1. Arka Prava Basu [arkaprava94@gmail.com](mailto:arkaprava94@gmail.com)
2. Bishakh Chandra Ghosh [ghoshbishakh@gmail.com](mailto:ghoshbishakh@gmail.com)


# Developer Instructions

The project uses maven for managing dependencies.

*Use java 1.7*

To build jar use `make package`.

To make the distributable use:
```
make package
make dist
```
