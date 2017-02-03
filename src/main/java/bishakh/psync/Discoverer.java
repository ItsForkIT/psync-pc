package bishakh.psync;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;


/**
 * The Discoverer module : Find Peers in communication ranges
 */
public class Discoverer {

    String BROADCAST_IP;
    int PORT;
    String PEER_ID;
    Logger logger;
    final Thread[] thread = new Thread[3];
    final BroadcastThread broadcastThread;
    final ListenThread listenThread;
    final PeerExpiryThread peerExpiryThread;

    // peerList format PEER_IP : [PEER_ID, timeAfterLastBroadcast]
    public volatile ConcurrentHashMap<String, ArrayList<String>> peerList;

    public Discoverer(String BROADCAST_IP, String PEER_ID, int PORT, Logger LoggerObj) {
        this.BROADCAST_IP = BROADCAST_IP;
        this.PORT = PORT;
        this.PEER_ID = PEER_ID;
        this.logger = LoggerObj;

        peerList = new ConcurrentHashMap<String, ArrayList<String>>();
        // peerList format PEER_IP : [PEER_ID, timeAfterLastBroadcast]
        broadcastThread = new BroadcastThread(BROADCAST_IP, PORT);
        listenThread = new ListenThread();
        peerExpiryThread = new PeerExpiryThread();
        thread[0] = new Thread(broadcastThread);
        thread[1] = new Thread(listenThread);
        thread[2] = new Thread(peerExpiryThread);
    }

    public void startBroadcast(){
        if (!broadcastThread.isRunning) {
            thread[0] = new Thread(broadcastThread);
            thread[0].start();
        }
    }

    public void stopBroadcast() {
        if(broadcastThread.isRunning) {
            broadcastThread.stop();
        }
    }

    public void startListener() {
        /*
        Check for any zombie thread waiting for broadcast
        If there is no zombie thread start a new thread to
        listen for broadcast
        else revive zombie thread
        */
        if (!listenThread.isRunning) {
            if (thread[1].isAlive()) {
                logger.d("LISTENER", "REVIVING");
                listenThread.revive();
            } else {
                logger.d("LISTENER", "STARTING");
                thread[1] = new Thread(listenThread);
                thread[1].start();
            }
        }
    }

    public void stopListener() {
        logger.d("LISTENER", "STOPPING");
        if (!listenThread.exit) {
            logger.d("LISTENER", "STOPPING2");
            listenThread.stop();
        }
    }

    public void startPeerExpiry(){
        if (!peerExpiryThread.isRunning) {
            thread[2] = new Thread(peerExpiryThread);
            thread[2].start();
        }
    }

    public void stopPeerExpiry() {
        if(peerExpiryThread.isRunning) {
            peerExpiryThread.stop();
        }
    }

    public void startDiscoverer(){
        startBroadcast();
        startListener();
        startPeerExpiry();
    }

    public void stopDiscoverer(){
        stopBroadcast();
        stopListener();
        stopPeerExpiry();
    }

    public boolean HPnodePresent(){
        boolean HPpresent = false;

        boolean defaultMCSPresent = false;
        boolean DBPresent = false;
        boolean mulePresent = false;


        if(this.PEER_ID.startsWith("DB")){
            // Check if node of higher priority than DB present
            return (mulePresent || defaultMCSPresent);
        }

        if(this.PEER_ID.startsWith("mule")){
            // Check if node of higher priority than DB present
            return defaultMCSPresent;
        }
        if(this.PEER_ID.startsWith("offlineMcs")){
            // Highest priority
            return false;
        }

        // For normal node:
        return (defaultMCSPresent || DBPresent || mulePresent);
    }


    /**
     * Thread to broadcast Datagram packets
     */
    public class BroadcastThread implements Runnable {
        String BROADCAST_IP;
        int PORT;
        DatagramSocket datagramSocket;
        byte buffer[] = null;
        DatagramPacket datagramPacket;
        volatile boolean exit;
        volatile boolean isRunning;

        public BroadcastThread(String BROADCAST_IP, int PORT) {
            this.BROADCAST_IP = BROADCAST_IP;
            this.PORT = PORT;
            this.exit = false;
            this.isRunning = false;
        }

        @Override
        public void run() {
            try {
                datagramSocket = new DatagramSocket();
                datagramSocket.setBroadcast(true);
                buffer = PEER_ID.getBytes("UTF-8");
                this.isRunning = true;
                while(!this.exit) {
                    datagramPacket = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(BROADCAST_IP), PORT);
                    try {
                        datagramSocket.send(datagramPacket);
                        logger.d("DEBUG", "Broadcast Packet Sent");
                    }
                    catch (Exception e){
                        e.printStackTrace();
                        logger.d("DEBUG", "Broadcast Packet Sending Failed");
                    }
                    Thread.sleep(3000);
                }
            } catch (SocketException e) {
                e.printStackTrace();
            } catch(UnknownHostException e){
                e.printStackTrace();
            }catch(IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                datagramSocket.close();
            }
            this.exit = false;
            this.isRunning = false;

            logger.d("DEBUG", "Broadcasting Stopped");
        }

        public void stop() {
            this.exit = true;
        }
    }


    /**
     * Thread to listen for broadcasts
     */
    public class ListenThread implements Runnable{
        DatagramPacket datagramPacket;
        byte buffer[];
        DatagramSocket datagramSocket;
        volatile boolean exit;
        volatile boolean isRunning;


        public ListenThread(){
            this.exit = false;
            this.isRunning = false;
        }

        @Override
        public void run() {
            try{
                datagramSocket = new DatagramSocket(PORT, InetAddress.getByName("0.0.0.0"));
                datagramSocket.setBroadcast(true);
                datagramSocket.setSoTimeout(200);

                logger.d("DEBUG", "ListenerThread Start");
                this.isRunning = true;
                while(!this.exit) {
                    buffer = new byte[15000];
                    boolean willUpdatePeer = false;
                    datagramPacket = new DatagramPacket(buffer, buffer.length);
                    try {
                        datagramSocket.receive(datagramPacket);
                        willUpdatePeer = true;          // datagram packet received will update peer list
                    }catch (IOException e) {
                        /*
                        This exception will be caught when we do not receive a datagram packet
                         */
                    }

                    if(willUpdatePeer) {
                        String peerID = new String(datagramPacket.getData(), 0, datagramPacket.getLength());
                        updatePeers(datagramPacket.getAddress().getHostAddress(), peerID);
                    }
                } // end of while
            }catch (UnknownHostException e){


            }catch (IOException e){


            } finally {
                try {
                    datagramSocket.close();
                }catch (NullPointerException e){

                }
            }
            this.exit = false;
            this.isRunning = false;
            logger.d("DEBUG", "ListenerThread Stop");

        }

        public void stop() {
            this.exit = true;
        }

        /**
         * Handle zombie state of listening thread
         */
        public void revive() {
            this.exit = false;
        }

        /**
         * Update list of peers
         * @param peerIP the ip address of the discovered peer
         * @param peerID id of the current peer
         */
        public void updatePeers(String peerIP, String peerID ) {
            /*
            Put the ip address in the table
            Set its counter to 0
             */
            ArrayList<String> l = new ArrayList<String>();
            l.add(peerID);
            l.add(0 + "");
            logger.d("DEBUG", "ListenerThread Receive Broadcaset:" + peerID);
            if(peerList.get(peerIP) == null){
                logger.write("PEER_DISCOVERED, " + peerID + ", " + peerIP);
            }
            peerList.put(peerIP, l);
        }
    }

    /*
     * Thread to expire peers after T seconds of inactivity
     */
    public class PeerExpiryThread implements Runnable {
        boolean exit = true;
        boolean isRunning = false;

        @Override
        public void run() {
            exit = false;
            isRunning = true;
            while (!exit) {
                for (String s : peerList.keySet()) {
                    ArrayList<String> l = peerList.get(s);
                    int time = Integer.parseInt(l.get(1));
                    if(time >= 10) {
                        logger.write("PEER_LOST, " + l.get(0) + ", " + s);
                        peerList.remove(s);
                        logger.d("DEBUG", "PeerExpiryThread Remove:" + l.get(0));
                    } else {
                        l.set(1, String.valueOf(time + 1));
                        peerList.put(s, l);
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            exit = false;
            isRunning = false;
        }

        public void stop() {
            exit = true;
        }
    }


}