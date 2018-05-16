package bishakh.psync;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
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
    Logger logger;
    MapDataProcessor mapDataProcessor;
    FilePriorityComparator filePriorityComparator;
    boolean restrictedEpidemic;
    int priorityMethod;

    ConcurrentHashMap<String, FileTable> remotePeerFileTableHashMap;

    /*
    missingFileTableHashMap Format :
    ----------------------------------------------------
    | Peer Address | File ID | File Table for the file |
    ----------------------------------------------------
     */
    ConcurrentHashMap<String, ConcurrentHashMap<String, FileEntry>> missingFileTableHashMap;


    /*
    fileTablePeerID Format :
    ---------------------------------------
    | File Table for the file | Peer Id
    ---------------------------------------
     */
    ConcurrentHashMap<FileEntry, String> fileTablePeerID;


    public Controller(Discoverer discoverer, FileManager fileManager, FileTransporter fileTransporter, int syncInterval,
                      int maxRunningDownloads, Logger LoggerObj, int priorityMethod, boolean restrictedEpidemic) {
        this.discoverer = discoverer;
        this.fileManager = fileManager;
        this.syncInterval = syncInterval;
        this.fileTransporter = fileTransporter;
        this.maxRunningDownloads = maxRunningDownloads;
        remotePeerFileTableHashMap = new ConcurrentHashMap<>();
        missingFileTableHashMap = new ConcurrentHashMap<>();
        fileTablePeerID = new ConcurrentHashMap<>();
        controllerThread = new ControllerThread(this);
        mcontrollerThread  = new Thread(controllerThread);
        this.logger = LoggerObj;
        this.restrictedEpidemic = restrictedEpidemic;
        filePriorityComparator = new FilePriorityComparator(fileManager, logger, priorityMethod);
        this.priorityMethod = priorityMethod;
        try {
            this.mapDataProcessor = new MapDataProcessor(fileManager.FILES_PATH, fileManager.MAP_DIR_PATH);
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    public List<String> urlResolver(String  uri){
        List<String> FileAndMime = new ArrayList<String>();

        String parameter = uri.substring(1);
        logger.d("DEBUG", "Controller URL Request recv: " + parameter);

        if(parameter.equals("list")){
            FileAndMime.add(0, fileManager.DATABASE_PATH);
            FileAndMime.add(1, "application/octet-stream");
            return FileAndMime;
        }

        if(parameter.substring(0, 7).equals("getFile")){
            String fileID = parameter.substring(8);
            logger.d("DEBUG", "Controller URL Request recv: FILEID: " + fileID);
            FileAndMime.add(0, fileManager.FILES_PATH + "/" + fileManager.fileTable.fileMap.get(fileID).getFilePath());
            FileAndMime.add(1, "application/octet-stream");
            return FileAndMime;
        }

        if(parameter.substring(0, 7).equals("getTile")){
            String tileID = parameter.substring(8);
            FileAndMime.add(0, fileManager.MAP_DIR_PATH + "tiles/" + tileID);
            FileAndMime.add(1, "application/octet-stream");
            return FileAndMime;
        }

        if(parameter.substring(0, 11).equals("getMapAsset")){
            String fileName = parameter.substring(12);
            FileAndMime.add(0, fileManager.MAP_DIR_PATH + fileName);
            FileAndMime.add(1, "text/html");
            if(parameter.endsWith(".css")){
                FileAndMime.set(1, "text/css");
            }
            if(parameter.endsWith(".html")){
                FileAndMime.set(1, "text/html");
            }
            return FileAndMime;
        }

        if(parameter.substring(0, 6).equals("getGIS")){
            // Example http://127.0.0.1:8080/getGIS/allLogs
            String gisObjName = parameter.substring(7);
            logger.d("GIS_FETCH ", gisObjName);
            if(gisObjName.equals("allLogs.txt")) {
                mapDataProcessor.generateAllTrails();
                FileAndMime.add(0, fileManager.MAP_DIR_PATH + gisObjName);
                FileAndMime.add(1, "text/plain");
                logger.d("RETURN GIS ", gisObjName);
                return FileAndMime;
            }
            if(gisObjName.equals("allGIS.txt")) {
                mapDataProcessor.generateAllGIS();
                FileAndMime.add(0, fileManager.MAP_DIR_PATH + gisObjName);
                FileAndMime.add(1, "text/plain");
                logger.d("RETURN GIS ", gisObjName);
                return FileAndMime;
            }
        }

        FileAndMime.add(0, "");
        FileAndMime.add(1, "");
        return FileAndMime;

    }

    /**
     * Collect the remote file info from the available peers
     * Called when ListFetcher thread has received the file list from peer
     * @param peerAddress : the address of the current peer
     * @param remoteFileTable : the fileTable of the current peer
     */
    void peerFilesFetched(String peerAddress, FileTable remoteFileTable) {
        Gson gson = new Gson();
        logger.d("DEBUG: ", "Controller file fetch from " + peerAddress);
        if(remotePeerFileTableHashMap != null) {
            remotePeerFileTableHashMap.put(peerAddress, remoteFileTable);
        }
    }

    /**
     * Find the missing / incomplete files from peers
     */
    void findMissingFiles() {
        missingFileTableHashMap.clear();
        long endByte;
        long remoteEndByte;
        /*
        Iterate over all peers
         */
        for(String peers : remotePeerFileTableHashMap.keySet()) {
            try{
                String thisPeerId = discoverer.originalPeerList.get(peers).get(0);
                if(thisPeerId.equals(discoverer.PEER_ID)){
                    continue;
                }
            }
            catch (Exception e){
                e.printStackTrace();
            }
            /*
            Iterate over all files
             */
            for( String files : remotePeerFileTableHashMap.get(peers).fileMap.keySet()) {
                /*
                Find whether the peer has any file which is missing in device
                 */
                if (remotePeerFileTableHashMap.get(peers).fileMap.get(files) != null){
                endByte = 0;
                boolean isMissing = true;
                remoteEndByte = remotePeerFileTableHashMap.get(peers).fileMap.get(files).getSequence().get(1);
                if(fileManager.fileTable.fileMap.get(files) != null) {
                        // check whether file in local file table
                        //logger.d("DEBUG: ", "MISSING FILE END BYTE : " + fileManager.fileTableHashMap.get(myFiles).getSequence().get(1));
                        //logger.d("DEBUG: ", "MISSING FILE SIZE " + fileManager.fileTableHashMap.get(myFiles).getFileSize());

                        if (remotePeerFileTableHashMap.get(peers).fileMap.get(files).getDestinationReachedStatus() && this.restrictedEpidemic) {
                            // file already reached dest
                            isMissing = false;
                            logger.d("DEBUG-------------------------->\n", "" + remotePeerFileTableHashMap.get(peers).fileMap.get(files).getDestinationReachedStatus() + remotePeerFileTableHashMap.get(peers).fileMap.get(files).getFilePath());
                            logger.d("DEBUG: ", "MISSING FILE ALREADY SENT TO DESTINATION - settingRestrictedEpedemicParameter");
                            fileManager.fileTable.fileMap.get(files).setDestinationReachedStatus(true);
                        }
                        else if (fileManager.fileTable.fileMap.get(files).getDestinationReachedStatus() && this.restrictedEpidemic) {
                            // file already reached dest
                            isMissing = false;
                            logger.d("DEBUG: ", "MISSING FILE ALREADY SENT TO DESTINATION");
                        }
                        else if (fileManager.fileTable.fileMap.get(files).getSequence().get(1) ==
                                fileManager.fileTable.fileMap.get(files).getFileSize()) { // complete file available
                            isMissing = false;
                            // logger.d("DEBUG: ", "MISSING FILE COMPLETE");
                        }
                        else {
                            if (fileManager.fileTable.fileMap.get(files).getSequence().get(1) <
                                    remotePeerFileTableHashMap.get(peers).fileMap.get(files).getSequence().get(1)) {
                                isMissing = true;
                                logger.d("DEBUG: ", "MISSING FILE INCOMPLETE");
                                endByte = fileManager.fileTable.fileMap.get(files).getSequence().get(1);
                            } else {
                                isMissing = false;
                            }
                        }
                    }
                if (isMissing) { // file is missing
                    //Log.d("DEBUG: ", "MISSING FILE TRUE");

                    // Mark missing only if remote peer has > 0 bit data
                    if (remoteEndByte > 0) {
                        // CHECK IF IT IS AN OLD GPS LOG
                        if (!fileManager.checkIfOldGPSLog(remotePeerFileTableHashMap.get(peers).fileMap.get(files).getFileName())) {
                            // check if already file has reached dest
                            if(!remotePeerFileTableHashMap.get(peers).fileMap.get(files).getDestinationReachedStatus() || !this.restrictedEpidemic){
                                if (missingFileTableHashMap.get(peers) == null) { // this is first missing file from current peer
                                    missingFileTableHashMap.put(peers, new ConcurrentHashMap<String, FileEntry>());
                                }
                                missingFileTableHashMap.get(peers).put(files, remotePeerFileTableHashMap.get(peers).fileMap.get(files));
                                // missing file sequence same as sequence of available file
                                List<Long> seq = new ArrayList<>();
                                seq.add((long) 0);
                                seq.add(endByte);
                                missingFileTableHashMap.get(peers).get(files).setSequence(seq);
                            }
                        }
                    }


                    // Make file manager entry
                    if (fileManager.fileTable.fileMap.get(files) == null) {
                        fileManager.fileTable.fileMap.put(files, remotePeerFileTableHashMap.get(peers).fileMap.get(files));
                        fileManager.forceSetEndSequence(files, endByte);
                    }

                }
            }
            }
        }

        // Put missing files in fileTable->PeerID map
        fileTablePeerID.clear();
        for(String p : missingFileTableHashMap.keySet()) {
            if(missingFileTableHashMap.get(p) != null) {
                for (String fileID : missingFileTableHashMap.get(p).keySet()) {
                    fileTablePeerID.put(missingFileTableHashMap.get(p).get(fileID), p);
                }
            }
        }
    }

    /**
     * Remove the peer which has expired
     */
    void removeExpiredRemoteFiles() {
        for(String peer : remotePeerFileTableHashMap.keySet()) {
            if(discoverer.priorityPeerList.get(peer) == null) { // the peer has expired
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
            logger.d("DEBUG: ", "Controller: DownloadPROGRESS " + downloadRunnable.getPresentByte());
            if(downloadRunnable.isRunning){
                fileManager.setEndSequence(downloadRunnable.fileID, downloadRunnable.getPresentByte());
            }
            else {
                fileManager.setEndSequence(downloadRunnable.fileID, downloadRunnable.getPresentByte());
                fileManager.checkDestinationReachStatus(downloadRunnable.fileID);
                /* Check and remove old gps log file from same node */
                if(downloadRunnable.filesize == downloadRunnable.getPresentByte()){
                    fileManager.removeOldGpsLogs(downloadRunnable.fileID);
                }

                fileTransporter.ongoingDownloadThreads.remove(t);
                logger.d("DEBUG: ", "Controller: DownloadThreadRemove " + downloadRunnable.getPresentByte());

            }
        }
    }

    void startDownloadingMissingFiles(){

        if(fileTransporter.ongoingDownloadThreads.size() < maxRunningDownloads) {

            /* With file priority */

            PriorityQueue<FileEntry> missingFileQueue = getMissingFileQueue();

            while ((!missingFileQueue.isEmpty()) && (fileTransporter.ongoingDownloadThreads.size() < maxRunningDownloads)){
                FileEntry fileToDownload = missingFileQueue.remove();
                boolean ongoing = false;
                for(Thread t : fileTransporter.ongoingDownloadThreads.keySet()){
                    logger.d("DEBUG: ", "MISSING FILE ONGOING CHECK" + fileToDownload.getFileID());
                    //Log.d("DEBUG: ", "MISSING FILE ONGOING" + fileTransporter.ongoingDownloadThreads.get(t).fileID );
                    if(fileTransporter.ongoingDownloadThreads.get(t).fileID.equals(fileToDownload.getFileID())){
                        ongoing = true;
                        break;
                    }
                }
                if(!ongoing){
                    try {
                        logger.write("DEBUG " + "Controller MISSING FILE START DOWNLOAD " + fileToDownload.getFileName() + " "+ fileToDownload.getImportance());
                        String peerIP = fileTablePeerID.get(fileToDownload);
                        String peerID = discoverer.originalPeerList.get(peerIP).get(0);
                        fileTransporter.downloadFile(fileToDownload.getFileID(),
                                fileToDownload.getFileName(), fileToDownload.getFilePath(),
                                peerIP, peerID, fileToDownload.getSequence().get(1),
                                -1, fileToDownload.getFileSize());
                        if(priorityMethod == 3){
                            enqueueKMLAssets(fileToDownload);
                        }
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    }
                }
            }



            /* end with file priority */

        }
    }

    void enqueueKMLAssets(FileEntry fileToDownload) {
        if (!fileToDownload.getFilePath().startsWith("SurakshitKml") && !fileToDownload.getFilePath().startsWith("SurakshitDiff")) {
            return;
        }
        String unique = fileToDownload.getFileName().split("_")[0];
        for (Map.Entry<String, ConcurrentHashMap<String, FileEntry>> peerFileTable : missingFileTableHashMap.entrySet()) {
            ConcurrentHashMap<String, FileEntry> fileTable = peerFileTable.getValue();
            String peerID = peerFileTable.getKey();
            for (FileEntry fileEntry : fileTable.values()) {
                String thisUnique = fileEntry.getFileName().split("_")[0];
                if(thisUnique.equals(unique)){

                    // Check if already ongoing download
                    boolean ongoing = false;
                    for(Thread t : fileTransporter.ongoingDownloadThreads.keySet()){
                        logger.d("DEBUG: ", "MISSING FILE ONGOING CHECK" + fileEntry.getFileID());
                        //Log.d("DEBUG: ", "MISSING FILE ONGOING" + fileTransporter.ongoingDownloadThreads.get(t).fileID );
                        if(fileTransporter.ongoingDownloadThreads.get(t).fileID.equals(fileEntry.getFileID())){
                            ongoing = true;
                            break;
                        }
                    }
                    if(!ongoing){
                        String peerIP = fileTablePeerID.get(fileEntry);
                        try {
                            fileTransporter.downloadFile(fileEntry.getFileID(),
                                    fileEntry.getFileName(), fileEntry.getFilePath(),
                                    peerIP, peerID, fileEntry.getSequence().get(1),
                                    -1, fileEntry.getFileSize());
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        }
                    }

                }
            }
        }
    }



    public PriorityQueue<FileEntry> getMissingFileQueue(){
        PriorityQueue<FileEntry> missingFileQueue = new PriorityQueue<FileEntry>(maxRunningDownloads, filePriorityComparator);

        for(String p : missingFileTableHashMap.keySet()) {

            for(String fileID : missingFileTableHashMap.get(p).keySet()) {
                missingFileQueue.add(missingFileTableHashMap.get(p).get(fileID));
            }
        }

        return  missingFileQueue;
    }


    /**
     * Thread to fetch the file list from all the available peers
     * Find the missing files from the peers
     */
    public class ControllerThread implements Runnable {
        boolean exit = true;
        boolean isRunning = false;
        Controller controller;
        int countMissingFileFetcher = 1;

        public ControllerThread(Controller controller) {
            this.controller = controller;
        }

        @Override
        public void run() {
            exit = false;
            isRunning = true;
            logger.d("DEBUG: ", "Controller thread running : " );
            while (!exit) {

                /*
                For every peer available fetch file list from them
                After that update remotePeerFileTableHashMap
                 */
                logger.d("DEBUG: ", "MISSING FILE CONTROL: " + countMissingFileFetcher);
                for(String s : discoverer.priorityPeerList.keySet()) {
                    if(remotePeerFileTableHashMap.get(s) != null && countMissingFileFetcher%4 != 0){
                        logger.d("DEBUG: ", "Controller skip fetching missing files for " + s);
                    }
                    else{
                        try {
                            new Thread(fileTransporter.new ListFetcher(controller, new URL("http://"+s+":8080/list"), s)).start();
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        }
                    }
                }

                if(countMissingFileFetcher%4 != 0){
                    countMissingFileFetcher+=1;
                }
                else {
                    countMissingFileFetcher = 1;
                }

                /*
                Remove the peers which have expired and the missing files corresponding to them
                Then find the missing files from the available peers
                 */
                removeExpiredRemoteFiles();
                manageOngoingDownloads();
                findMissingFiles();
                Gson gson = new Gson();
                logger.d("DEBUG: ", "Controller thread missing files : " + gson.toJson(missingFileTableHashMap).toString());
                logger.d("DEBUG:", "MISSING FILES ONGOING THREAD COUNT" + fileTransporter.ongoingDownloadThreads.size());
                startDownloadingMissingFiles();

                try {
                    Thread.sleep(syncInterval * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            exit = false;
            isRunning = false;
            logger.d("DEBUG: ", "Controller thread stopped " );
        }

        public void stop() {
            exit = true;
        }
    }
}
