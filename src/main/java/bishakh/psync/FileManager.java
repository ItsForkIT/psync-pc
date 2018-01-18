package bishakh.psync;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File Table and File List Format:
 * ---------------------------------------------------------------------------------------------------------------------
 * |  File ID  |  File Name  |  Sequence  |  File Size  | Priority | Timestamp | TTL | Destination | DestReachedStatus |
 * ---------------------------------------------------------------------------------------------------------------------
 */
public class FileManager {

    public FileTable fileTable;
    Gson gson = new Gson();
    final String DATABASE_NAME;
    final String DATABASE_PATH;
    final String MAP_DIR_PATH;
    final File FILES_PATH;
    final String PEER_ID;
    Logger logger;
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

    HashMap<Set<String>, Integer> countOfTypesWithSpace;
    double maxImportanceOfaTile;

    private FileManagerThread fileManagerThread = new FileManagerThread();

    public FileManager(String peerID, String databaseName, String databaseDirectory, String syncDirectory, String mapDir, Logger loggerObj){
        this.PEER_ID = peerID;
        this.fileTable = new FileTable(peerID);
        fileTable.fileMap = new ConcurrentHashMap<>();
        this.DATABASE_NAME = databaseName;
        this.DATABASE_PATH = databaseDirectory + DATABASE_NAME;
        this.FILES_PATH = new File(syncDirectory);
        this.logger = loggerObj;
        this.MAP_DIR_PATH = mapDir;
        logger.d("DEBUG", " Starting FileManager with directories: " + this.FILES_PATH + ", " + this.DATABASE_PATH);
        readDB();
        this.countOfTypesWithSpace = new HashMap<>();
    }

    public void startFileManager(){
        if (!fileManagerThread.isRunning) {
            Thread fileManagerThreadInstance = new Thread(fileManagerThread);
            fileManagerThreadInstance.start();
        }
    }

    public void stopFileManager() {
        if(fileManagerThread.isRunning) {
            fileManagerThread.stop();
        }
    }

    public class FileManagerThread implements Runnable {
        private boolean exit;
        private boolean isRunning;

        public FileManagerThread() {
            this.exit = true;
            this.isRunning = false;
        }

        @Override
        public void run() {
            int count = 6;
            try {
                logger.d("DEBUG", "FileManager Thread started");
                this.exit = false;
                this.isRunning = true;
                while(!this.exit) {
                    logger.d("DEBUG", "FileManager Scanning..");
                    updateFilesFromSubfolders(FILES_PATH, FILES_PATH);
                    removeDeletedFiles();
                    writeDB();
                    if(count > 4){
                        updateImportanceOfFilesAndTiles();
                        count = 0;
                    }
                    count += 1;
                    Thread.sleep(5*1000);
                }
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }

            this.exit = false;
            this.isRunning = false;

            logger.d("DEBUG", "FileManager Thread Stopped");
        }

        public void stop() {
            this.exit = true;
        }
    }

    public String getFileDBJson(){
        return gson.toJson(fileTable);
    }


    /**
     * Store file description
     * @param fileID
     * @param fileName
     * @param sequence
     * @param fileSize
     * @param priority
     * @param timestamp
     * @param ttl
     * @param destination
     * @param destinationReachedStatus
     */
    private void enterFile(String fileID, String fileName, String filePath, List<Long> sequence, double fileSize, int priority,
                          String timestamp, int ttl, String destination, boolean destinationReachedStatus, double importance){
        FileEntry newFileInfo = new FileEntry( fileID, fileName, filePath, sequence, fileSize, priority, timestamp,
                ttl, destination, destinationReachedStatus, importance);
        fileTable.fileMap.put( fileID, newFileInfo);
        checkDestinationReachStatus(fileID);
        logger.d("DEBUG", "FileManager Add to DB: " + fileName);
    }

    public void setEndSequence(String fileID, long endByte){
        FileEntry fileEntry = fileTable.fileMap.get(fileID);
        if(fileEntry != null){
            List<Long> prevSeq = fileEntry.getSequence();
            List<Long> newSeq = new ArrayList<Long>();
            newSeq.add(0, prevSeq.get(0));
            if(endByte > prevSeq.get(1)) {
                newSeq.add(1, endByte);
            }
            else {
                newSeq.add(1, prevSeq.get(1));
            }

            fileEntry.setSequence(newSeq);
            logger.d("DEBUG", "FileManager SET_END_SEQ: " + newSeq);
        }
    }

    public void forceSetEndSequence(String fileID, long endByte){
        FileEntry fileEntry = fileTable.fileMap.get(fileID);
        if(fileTable != null){
            List<Long> newSeq = new ArrayList<Long>();
            newSeq.add(0, (long)0); // check this

            newSeq.add(1, endByte);

            fileEntry.setSequence(newSeq);
            logger.d("DEBUG", "FileManager SET_END_SEQ: " + newSeq);
        }
    }

    /**
     * Serialize data
     */
    private void writeDB() {
        try{
            File file = new File(DATABASE_PATH);
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(gson.toJson(fileTable));
            fileWriter.flush();
            fileWriter.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Deserialize data
     */
    private void readDB() {
        logger.d("DEBUG", "FileManager reading from fileDB");
        try{
            BufferedReader br = new BufferedReader(new FileReader(DATABASE_PATH));

            //convert the json string back to object
            fileTable = (FileTable)gson.fromJson(br, FileTable.class);
            fileTable.peerID = this.PEER_ID;

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        catch (NullPointerException e) {
            this.writeDB();
            this.fileTable = new FileTable(this.PEER_ID);
            this.fileTable.fileMap = new ConcurrentHashMap();
        }
    }

    /**
     * Traverse the folder and add / remove files
     */

    private void updateFilesFromSubfolders(File base_path, File folder_path ) {
//        logger.d("DEBUG: ", "UPDATING FILES FROM SYNC DIRECTORY TREE");
        if (folder_path.isDirectory()) {
//            Folder Detected
            String[] subFileFolders = folder_path.list();
            for (String filename : subFileFolders) {
                updateFilesFromSubfolders(base_path, new File(folder_path, filename));
            }
        } else {
//            File Detected
            File file_path = folder_path;
            String relative_path = new File(base_path.getAbsolutePath()).toURI().relativize(new File(file_path.getAbsolutePath()).toURI()).getPath();
//            logger.d("DEBUG: ---------------------->", relative_path + " " + file_path.getName());

//            ENTER INTO FILETABLE
            String fileID = getFileIDFromPath(file_path);
            if (fileTable.fileMap.get(fileID) == null || fileTable.fileMap.get(fileID).getSequence().get(1) == 0) {
                long fileSize = file_path.length();
                List seq = new ArrayList();
                seq.add(0, 0);
                seq.add(1, fileSize);
                String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
                String ttl;
                try {
                    ttl = file_path.getName().split("_")[1];
                    int i = Integer.parseInt(ttl);
                } catch (Exception e) {
                    ttl = "50";
                }

                String destination;
                try {
                    destination = file_path.getName().split("_")[4];
                }
                catch (Exception e){
                    destination = "ALL";
                }

                double imp = -1;

                if (file_path.getName().startsWith("GIS_")) {
                    imp = 9999999999999.99;
                }
                if (file_path.getName().startsWith("MapDisarm_Log_")) {
                    imp = 99999999999999.99;
                }
                logger.d("DEBUG: " , "FileID: " + fileID +
                        "\n FileName: "+  file_path.getName() +
                        "\nRelativePath: " + relative_path +
                         "\nSEQ: " +seq +
                        "\nSIZE: " + fileSize+
                        "\nTTL-priority: " + Integer.parseInt(ttl) +
                        "\nTimestamp: " +timeStamp +
                        "\nTTL: " +ttl +
                        "\nDEST: " +destination +
                        "\nDestinationReachStatus: " +false +
                        "\nImportance: " +imp);
                enterFile(fileID, file_path.getName(), relative_path, seq, fileSize, Integer.parseInt(ttl), timeStamp, Integer.parseInt(ttl), destination, false, imp);

            }
        }
    }


    private void removeDeletedFiles(){
        for (String key : fileTable.fileMap.keySet()) {
            FileEntry fileInfo = fileTable.fileMap.get(key);
            String filePath = fileInfo.getFilePath();
            List<Long> seq = fileInfo.getSequence();
            if(seq.get(1) != 0){
                boolean check = new File(FILES_PATH + File.separator + filePath).exists();
                if(!check){
                    fileTable.fileMap.remove(key);
                    logger.d("DEBUG", "FileManaager Remove from DB " + filePath);

                }
            }
        }

    }

    private String getFileIDFromPath(File file){
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        byte[] md5sum = md.digest(file.getName().getBytes());
        // Create Hex String
        StringBuffer hexString = new StringBuffer();

        for (int i=0; i<md5sum.length; i++) {
            hexString.append(Integer.toHexString(0xFF & md5sum[i]));
        }

        return hexString.toString();
    }

    public void checkDestinationReachStatus(String fileID){
        FileEntry fileEntry = fileTable.fileMap.get(fileID);
        if(fileEntry!=null) {
            logger.d("DEBUG: ", "CheckingDestinationReachStatus " + fileEntry.getDestination() + " vs " + PEER_ID);
            if (fileEntry.getDestination().equals(PEER_ID) && fileEntry.getSequence().get(1) == fileEntry.getFileSize()) {
                logger.d("DEBUG: ", "DESTINATION REACHED - " + fileEntry.getFileName());
                fileEntry.setDestinationReachedStatus(true);
            }
        }
    }


    public void removeOldGpsLogs(String fileID){

        FileEntry newLogFileEntry = fileTable.fileMap.get(fileID);
        if(newLogFileEntry==null){
            return;
        }
        final String newLogName = newLogFileEntry.getFileName();

        if(newLogName.startsWith("MapDisarm_Log")) {
            /* find all log files of same node*/
            logger.d("DEBUG", "REMOVING OLD LOG FILES IF ANY");
            File[] logFileList = this.FILES_PATH.listFiles(new FilenameFilter() {
                public boolean accept(File file, String s) {
                    return s.startsWith(newLogName.substring(0, newLogName.length() - 19));
                }
            });

            long latestdate = 0;
            /* Keep the latest log */
            for (File file : logFileList) {
                logger.d("CHECKING LOG" , file.getName());
                String thisFileDate = file.getName().split("_")[3];
                // now exclude .txt:
                thisFileDate = thisFileDate.substring(4, thisFileDate.length() - 4);
                if(latestdate < Integer.parseInt(thisFileDate)){
                    latestdate = Long.parseLong(thisFileDate);
                }
            }

            /* now remove all old logs from this node */
            for (File file : logFileList) {
                String thisFileDate = file.getName().split("_")[3];
                // now exclude .txt:
                thisFileDate = thisFileDate.substring(4, thisFileDate.length() - 4);
                if(latestdate != Long.parseLong(thisFileDate)){
                    logger.write("REMOVING LOG" + file.getName());
                    String fileid = getFileIDFromPath(file);
                    file.delete();
                    fileTable.fileMap.remove(fileid);
                }
                else {
                    logger.write("KEEPING LOG" + file.getName());
                }
            }

        }
    }

    public boolean checkIfOldGPSLog(final String fileName){

        if(fileName.startsWith("MapDisarm_Log")) {
            /* find all log files of same node*/
            logger.write("CHECKING OLD LOG FILES IF ANY");

            File[] logFileList = this.FILES_PATH.listFiles(new FilenameFilter() {
                public boolean accept(File file, String s) {
                    return s.startsWith(fileName.substring(0, fileName.length()-19));
                }
            });
            long latestdate = 0;
            /* Keep the latest log */
            for (File file : logFileList) {
                logger.d("CHECKING LOG" , file.getName());
                String thisFileDate = file.getName().split("_")[3];
                // now exclude .txt:
                thisFileDate = thisFileDate.substring(4, thisFileDate.length() - 4);
                if(latestdate < Integer.parseInt(thisFileDate)){
                    latestdate = Long.parseLong(thisFileDate);
                }
            }

            /* now check if this is old */
            String inputFileDate = fileName.split("_")[3];
            inputFileDate = inputFileDate.substring(4, inputFileDate.length() - 4);
                if(latestdate > Long.parseLong(inputFileDate)){
                    return true;
                }
                else {
                    return false;
                }

        }
        else {
            return false;
        }
    }

    public String getTileXYString(double lat, double lon, int zoom){

        int xtile = (int)Math.floor( (lon + 180) / 360 * (1<<zoom) ) ;
        int ytile = (int)Math.floor( (1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1<<zoom) ) ;
        if (xtile < 0)
            xtile=0;
        if (xtile >= (1<<zoom))
            xtile=((1<<zoom)-1);
        if (ytile < 0)
            ytile=0;
        if (ytile >= (1<<zoom))
            ytile=((1<<zoom)-1);
        return("" + zoom + "-" + xtile + "-" + ytile + ".topojson");
    }

    public void updateCountOfTypesWithSpace(){
        countOfTypesWithSpace.clear();

        for (String fileID : fileTable.fileMap.keySet()) {
            FileEntry fileEntry = fileTable.fileMap.get(fileID);
            String fileName = fileEntry.getFileName();
            if((fileName.startsWith("IMG_") || fileName.startsWith("VID_") || fileName.startsWith("SVS_") ||
                    fileName.startsWith("TXT_") || fileName.startsWith("SMS_")) && fileEntry.getSequence().get(1) > 0){
            if(!fileName.contains("null")){
                    Set<String> typeSpaceSet = new HashSet<>();
                    String typesString = fileName.split("_")[2];
                    String[] typesArray = typesString.split("-");
                    for(String typeStr:typesArray ){
                        typeSpaceSet.add(typeStr);
                    }
                    double lat = Double.parseDouble(fileName.split("_")[5]);
                    double lon = Double.parseDouble(fileName.split("_")[6]);
                    String tileName = getTileXYString(lat, lon, 16);
                    typeSpaceSet.add(tileName);

                    if(countOfTypesWithSpace.get(typeSpaceSet) == null){
                        countOfTypesWithSpace.put(typeSpaceSet, 0);
                    }
                    countOfTypesWithSpace.put(typeSpaceSet, countOfTypesWithSpace.get(typeSpaceSet) + 1);
                }
            }
        }
    }





    public void updateImportanceOfFilesAndTiles(){
        logger.d("DEBUG: ", " CALCULATING FILE IMPORTANCE");
        updateCountOfTypesWithSpace();
        int totalCountOfAllTypeSets = 0;
        maxImportanceOfaTile = 0;
        for( Set<String> typeSet:countOfTypesWithSpace.keySet()){
            totalCountOfAllTypeSets = totalCountOfAllTypeSets + countOfTypesWithSpace.get(typeSet);
        }

        for (String fileID : fileTable.fileMap.keySet()) {
            FileEntry fileEntry = fileTable.fileMap.get(fileID);
            String fileName = fileEntry.getFileName();
            if(fileName.startsWith("IMG_") || fileName.startsWith("VID_") || fileName.startsWith("SVS_") ||
                    fileName.startsWith("TXT_") || fileName.startsWith("SMS_")){
            if(fileEntry.getSequence().get(1) != 0){
            if(!fileName.contains("null")){
                    // Calculate Rank if the file is at least partially received

                    String typesString = fileName.split("_")[2];
                    String[] typesArray = typesString.split("-");
                    Set<String> typeSpaceSet = new HashSet<>();
                    for(String typeStr:typesArray ){
                        typeSpaceSet.add(typeStr);
                    }
                    double lat_ = Double.parseDouble(fileName.split("_")[5]);
                    double lon_ = Double.parseDouble(fileName.split("_")[6]);
                    String tileSpaceName = getTileXYString(lat_, lon_, 16);
                    typeSpaceSet.add(tileSpaceName);

                    int countOfTypeSetInThisFile = countOfTypesWithSpace.get(typeSpaceSet);

                    double proportion = (double) countOfTypeSetInThisFile / (double) totalCountOfAllTypeSets;
                    logger.d("ImportanceCalculator: ", "Proportion of " + typesString + " : " + proportion);

                    double informationOfFile = (double)(-1) * (Math.log(proportion) / Math.log((double)2));
                    if(informationOfFile == -0.0){
                        informationOfFile = 0;
                    }
                    logger.d("ImportanceCalculator: ", "Information of " + typesString + " : " + informationOfFile);

                    Date originDate = null;

                    try {
                        originDate = dateFormat.parse(fileName.split("_")[7]);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }

                    if(originDate != null){
                        // calculate aging and set importance
                        Date currentDate = new Date();
                        double ageInSeconds = (currentDate.getTime() - originDate.getTime())/1000.0;
                        double ageInHours = ageInSeconds / (double) 3600.0;
                        logger.d("ImportanceCalculator: ", "Age of " + fileName + " : " + ageInSeconds);

                        double importanceValue = informationOfFile * Math.pow(2.71828, ((double)(-1.0) * ageInHours));
                        logger.write("SET FILE IMPORTANCE " + fileName + " " + importanceValue);
                        fileTable.fileMap.get(fileID).setImportance(importanceValue);
                    }

                }
            }
            }
        }
    }


}


class FileTable implements java.io.Serializable{
    public String peerID;
    public ConcurrentHashMap<String, FileEntry> fileMap;

    public FileTable(String peerId){
        this.peerID = peerId;
        this.fileMap = new ConcurrentHashMap<String, FileEntry>();
    }
}


/**
 * Class to save file description
 */
class FileEntry implements java.io.Serializable{
    private String fileID;
    private String fileName;
    private String filePath;
    private List<Long> sequence;
    private double fileSize;
    private int priority;
    private String timestamp;
    private int ttl;
    private String destination;
    private boolean destinationReachedStatus;
    private double importance;

    public FileEntry(String fileID, String fileName, String filePath, List<Long> sequence, double fileSize, int priority,
                     String timestamp, int ttl, String destination, boolean destinationReachedStatus, double importance){
        this.fileID = fileID;
        this.fileName = fileName;
        this.filePath = filePath;
        this.sequence = sequence;
        this.fileSize = fileSize;
        this.priority = priority;
        this.timestamp = timestamp;
        this.ttl = ttl;
        this.destination = destination;
        this.destinationReachedStatus = destinationReachedStatus;
        this.importance = importance;
    }

    String getFileID(){
        return this.fileID;
    }

    String getFileName(){
        return this.fileName;
    }

    String getFilePath(){
        return this.filePath;
    }

    List<Long> getSequence() {
        return this.sequence;
    }

    double getFileSize(){
        return this.fileSize;
    }

    int getPriority(){
        return this.priority;
    }

    String getTimestamp(){
        return this.timestamp;
    }

    int getTtl(){
        return this.ttl;
    }

    String getDestination(){
        return this.destination;
    }

    boolean getDestinationReachedStatus(){
        return this.destinationReachedStatus;
    }

    double getImportance() { return  this.importance; }

    void setImportance(double imp) {
        if(Double.isInfinite(imp) || Double.isNaN(imp)){
            imp = 0;
        }
        this.importance = imp;
    }

    void setTtl(int ttl) {
        this.ttl = ttl;
    }

    void setSequence(List<Long> sequence){
        this.sequence = sequence;
    }

    void setDestinationReachedStatus(boolean status){
        this.destinationReachedStatus = status;
    }

}
