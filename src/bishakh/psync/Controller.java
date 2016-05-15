package bishakh.psync;

import com.google.gson.Gson;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Controller module : Core module that takes care
 * of the role based and device priority scheduling
 */
public class Controller {

    Discoverer discoverer;
    FileManager fileManager;
    FileTransporter fileTransporter;
    int syncInterval;
    ControllerThread controllerThread = new ControllerThread(this);
    Thread mcontrollerThread = new Thread(controllerThread);
    int maxRunningDownloads;

    ConcurrentHashMap<String, ConcurrentHashMap<String, FileTable>> remotePeerFileTableHashMap;
    ConcurrentHashMap<String, ConcurrentHashMap<String, FileTable>> missingFileTableHashMap;
    /*
    missingFileTableHashMap Format :
    ----------------------------------------------------
    | Peer Address | File ID | File Table for the file |
    ----------------------------------------------------
     */

    ConcurrentHashMap<String, FileTable> priorityDownloadList;
    /*
    priorityDownloadList Format :
    ---------------------------------------
    | Priority | File Table for the file |
    ---------------------------------------
     */
    ConcurrentHashMap<FileTable, String> fileTablePeerID;
    /*
    fileTablePeerID Format :
    ---------------------------------------
    | File Table for the file | Peer Id
    ---------------------------------------
     */

    public Controller(Discoverer discoverer, FileManager fileManager, FileTransporter fileTransporter, int syncInterval, int maxRunningDownloads) {
        this.discoverer = discoverer;
        this.fileManager = fileManager;
        this.syncInterval = syncInterval;
        this.fileTransporter = fileTransporter;
        this.maxRunningDownloads = maxRunningDownloads;
        remotePeerFileTableHashMap = new ConcurrentHashMap<>();
        missingFileTableHashMap = new ConcurrentHashMap<>();
        controllerThread = new ControllerThread(this);
        mcontrollerThread  = new Thread(controllerThread);
    }


    public void startController(){
        if(!controllerThread.isRunning){
            mcontrollerThread = new Thread(controllerThread);
            mcontrollerThread.start();
        }
    }

    public void stopController(){
        if(controllerThread.isRunning){
            controllerThread.stop();
        }
    }

    public String urlResolver(String  uri){
        String parameter = uri.substring(1);
        Log.d("DEBUG", "Controller URL Request recv: " + parameter);
        if(parameter.equals("list")){
            return fileManager.DATABASE_PATH;
        }
        else {
            if(parameter.substring(0, 7).equals("getFile")){
                String fileID = parameter.substring(8);
                Log.d("DEBUG", "Controller URL Request recv: FILEID: " + fileID);
                return fileManager.FILES_PATH + "/" + fileManager.fileTableHashMap.get(fileID).getFileName();
            }

            return "";
        }
    }

    /**
     * Collect the remote file info from the available peers
     * Called when ListFetcher thread has received the file list from peer
     * @param peerAddress : the address of the current peer
     * @param remoteFiles : the fileTable of the current peer
     */
    void peerFilesFetched(String peerAddress, ConcurrentHashMap<String, FileTable> remoteFiles) {
        Gson gson = new Gson();
        Log.d("DEBUG: ", "Controller file fetch Response code : " + gson.toJson(remoteFiles).toString());
        if(remotePeerFileTableHashMap.get(peerAddress) != null){
            remotePeerFileTableHashMap.remove(peerAddress);
        }
        remotePeerFileTableHashMap.put(peerAddress, remoteFiles);
    }

    /**
     * Find the missing / incomplete files from peers
     */
    void findMissingFiles() {
        missingFileTableHashMap.clear();
        long endByte;
        /*
        Iterate over all peers
         */
        for(String peers : remotePeerFileTableHashMap.keySet()) {
            /*
            Iterate over all files
             */
            for( String files : remotePeerFileTableHashMap.get(peers).keySet()) {
                /*
                Find whether the peer has any file which is missing in device
                 */
                endByte = 0;
                boolean isMissing = true;
                for(String myFiles : fileManager.fileTableHashMap.keySet()) {
                    if(files.equals(myFiles) == true) { // check whether file is same as remote file
                        Log.d("DEBUG: ", "MISSING FILE END BYTE : " + fileManager.fileTableHashMap.get(myFiles).getSequence().get(1));
                        Log.d("DEBUG: ", "MISSING FILE SIZE " + fileManager.fileTableHashMap.get(myFiles).getFileSize());

                        if (fileManager.fileTableHashMap.get(myFiles).getSequence().get(1) ==
                                fileManager.fileTableHashMap.get(myFiles).getFileSize()) { // complete file available
                            isMissing = false;
                            Log.d("DEBUG: ", "MISSING FILE COMPLETE");
                            break;
                        }else {
                            if (fileManager.fileTableHashMap.get(myFiles).getSequence().get(1) <
                                    remotePeerFileTableHashMap.get(peers).get(files).getSequence().get(1)) {
                                isMissing = true;
                                Log.d("DEBUG: ", "MISSING FILE INCOMPLETE");
                                endByte = fileManager.fileTableHashMap.get(myFiles).getSequence().get(1);
                                break;
                            } else {
                                isMissing = false;
                                break;
                            }
                        }
                    }
                }
                if(isMissing) { // file is missing
                    //Log.d("DEBUG: ", "MISSING FILE TRUE");
                    if(missingFileTableHashMap.get(peers) == null) { // this is first missing file from current peer
                        missingFileTableHashMap.put(peers, new ConcurrentHashMap<String, FileTable>());
                    }
                    missingFileTableHashMap.get(peers).put(files, remotePeerFileTableHashMap.get(peers).get(files));
                    // missing file sequence same as sequence of available file
                    List<Long> seq = new ArrayList<>();
                    seq.add((long) 0);
                    seq.add(endByte);
                    missingFileTableHashMap.get(peers).get(files).setSequence(seq);

                    // Make file manager entry
                    if(fileManager.fileTableHashMap.get(files) == null){
                        fileManager.fileTableHashMap.put(files, missingFileTableHashMap.get(peers).get(files));
                        fileManager.forceSetEndSequence(files, endByte);
                    }

                }
            }
        }
    }

    /**
     * Remove the peer which has expired
     */
    void removeExpiredRemoteFiles() {
        for(String peer : remotePeerFileTableHashMap.keySet()) {
            if(discoverer.peerList.get(peer) == null) { // the peer has expired
                remotePeerFileTableHashMap.remove(peer);
            }
        }
    }



    /*
     * Remove completed downloads from fileTransporter ongoingDownloadThreads list
     */
    void manageOngoingDownloads(){
        for(Thread t : fileTransporter.ongoingDownloadThreads.keySet()){
            FileTransporter.ResumeDownloadThread downloadRunnable = fileTransporter.ongoingDownloadThreads.get(t);
            //Log.d("DEBUG: ", "Controller: PROGRESS " + downloadRunnable.getPresentByte());
            if(downloadRunnable.isRunning){
                fileManager.setEndSequence(downloadRunnable.fileID, downloadRunnable.getPresentByte());
            }
            else {
                fileManager.setEndSequence(downloadRunnable.fileID, downloadRunnable.getPresentByte());
                fileTransporter.ongoingDownloadThreads.remove(t);
            }
        }
    }

    void startDownloadingMissingFiles(){
        if(fileTransporter.ongoingDownloadThreads.size() < maxRunningDownloads) {
            priorityDownloadList.clear();
            fileTablePeerID.clear();
            // arrange files to be downloaded according to priority
            for(String p : missingFileTableHashMap.keySet()) {
                for(String fileID : missingFileTableHashMap.get(p).keySet()) {
                    int priority = missingFileTableHashMap.get(p).get(fileID).getPriority();
                    //String priorityPeerID = "" + priority + "###" + p; // keep a combination of file priority and peer id
                    priorityDownloadList.put(""+priority, missingFileTableHashMap.get(p).get(fileID));
                    fileTablePeerID.put(missingFileTableHashMap.get(p).get(fileID), p);
                }
            }

            // sort this list according to priority
            Map<String, FileTable> priorityDownloadListSorted = new TreeMap<>(priorityDownloadList);

            // start download
            for(String priority : priorityDownloadListSorted.keySet()) {
                if(fileTransporter.ongoingDownloadThreads.size() >= maxRunningDownloads){
                    break;
                }
                boolean ongoing = false;
                for(Thread t : fileTransporter.ongoingDownloadThreads.keySet()) {
                    if(fileTransporter.ongoingDownloadThreads.get(t).fileID.equals(priorityDownloadListSorted.get(priority).getFileID())){
                        ongoing = true;
                        break;
                    }
                }
                if(!ongoing){
                    try {
                        Log.d("DEBUG: ", "Controller MISSING FILE START DOWNLOAD" );
                        //String peerID = priorityPeerID.substring(priorityPeerID.indexOf("###") + 3); // extract peer id

                        // we have files sorted according to priority ... use fileTablePeerID to get the peer id of the files
                        fileTransporter.downloadFile(priorityDownloadListSorted.get(priority).getFileID(),
                                priorityDownloadListSorted.get(priority).getFileName(),
                                fileTablePeerID.get(priorityDownloadListSorted.get(priority)),
                                priorityDownloadListSorted.get(priority).getSequence().get(1),
                                -1);
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    }
                }
            }
            /*
            for(String p : missingFileTableHashMap.keySet()) {
                if(fileTransporter.ongoingDownloadThreads.size() >= maxRunningDownloads){
                    break;
                }
                for(String fileID : missingFileTableHashMap.get(p).keySet()){
                    if(fileTransporter.ongoingDownloadThreads.size() >= maxRunningDownloads){
                        break;
                    }
                    boolean ongoing = false;
                    for(Thread t : fileTransporter.ongoingDownloadThreads.keySet()){
                        Log.d("DEBUG: ", "MISSING FILE ONGOING CHECK" + fileID);
                        //Log.d("DEBUG: ", "MISSING FILE ONGOING" + fileTransporter.ongoingDownloadThreads.get(t).fileID );
                        if(fileTransporter.ongoingDownloadThreads.get(t).fileID.equals(fileID)){
                            ongoing = true;
                            break;
                        }
                    }
                    if(!ongoing){
                        try {
                            Log.d("DEBUG: ", "Controller MISSING FILE START DOWNLOAD" );

                            fileTransporter.downloadFile(fileID, missingFileTableHashMap.get(p).get(fileID).getFileName(),p, missingFileTableHashMap.get(p).get(fileID).getSequence().get(1), -1);
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }*/
        }
    }

    /**
     * Thread to fetch the file list from all the available peers
     * Find the missing files from the peers
     */
    public class ControllerThread implements Runnable {
        boolean exit = true;
        boolean isRunning = false;
        Controller controller;

        public ControllerThread(Controller controller) {
            this.controller = controller;
        }

        @Override
        public void run() {
            exit = false;
            isRunning = true;
            Log.d("DEBUG: ", "Controller thread running : " );
            while (!exit) {

                /*
                For every peer available fetch file list from them
                After that update remotePeerFileTableHashMap
                 */
                for(String s : discoverer.peerList.keySet()) {
                    try {
                        new Thread(fileTransporter.new ListFetcher(controller, new URL("http://"+s+":8080/list"), s)).start();
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    }
                }

                /*
                Remove the peers which have expired and the missing files corresponding to them
                Then find the missing files from the available peers
                 */
                removeExpiredRemoteFiles();
                manageOngoingDownloads();
                findMissingFiles();
                Gson gson = new Gson();
                Log.d("DEBUG: ", "Controller thread missing files : " + gson.toJson(missingFileTableHashMap).toString());
                Log.d("DEBUG:", "MISSING FILES ONGOING THREAD COUNT" + fileTransporter.ongoingDownloadThreads.size());
                startDownloadingMissingFiles();

                try {
                    Thread.sleep(syncInterval * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            exit = false;
            isRunning = false;
            Log.d("DEBUG: ", "Controller thread stopped " );
        }

        public void stop() {
            exit = true;
        }
    }
}
