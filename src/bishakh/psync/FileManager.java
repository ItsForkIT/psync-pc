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

    ConcurrentHashMap<String, FileTable> fileTableHashMap;
    Type ConcurrentHashMapType = new TypeToken<ConcurrentHashMap<String, FileTable>>(){}.getType();
    Gson gson = new Gson();
    final String DATABASE_NAME;
    final String DATABASE_PATH;
    final String MAP_DIR_PATH;
    final File FILES_PATH;
    Logger logger;
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

    HashMap<Set<String>, Integer> countOfTypes;
    HashMap <String, Double> impOfFilesInTile;
    HashMap <String, Double> diffImpOfFilesInTile;
    double maxImportanceOfaTile;

    private FileManagerThread fileManagerThread = new FileManagerThread();

    public FileManager(String databaseName, String databaseDirectory, String syncDirectory, String mapDir, Logger loggerObj){
        this.fileTableHashMap = new ConcurrentHashMap<>();
        this.DATABASE_NAME = databaseName;
        this.DATABASE_PATH = databaseDirectory + DATABASE_NAME;
        this.FILES_PATH = new File(syncDirectory);
        this.logger = loggerObj;
        this.MAP_DIR_PATH = mapDir;
        logger.d("DEBUG", " Starting FileManager with directories: " + this.FILES_PATH + ", " + this.DATABASE_PATH);
        readDB();
        this.countOfTypes = new HashMap<>();
        this.impOfFilesInTile = new HashMap<>();
        this.diffImpOfFilesInTile = new HashMap<>();
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
                    updateFromFolder();
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
        return gson.toJson(fileTableHashMap);
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
    private void enterFile(String fileID, String fileName, List<Long> sequence, double fileSize, int priority,
                          String timestamp, String ttl, String destination, boolean destinationReachedStatus, double importance){
        FileTable newFileInfo = new FileTable( fileID, fileName, sequence, fileSize, priority, timestamp,
                ttl, destination, destinationReachedStatus, importance);
        fileTableHashMap.put( fileID, newFileInfo);
        logger.d("DEBUG", "FileManager Add to DB: " + fileName);
    }

    public void setEndSequence(String fileID, long endByte){
        FileTable fileTable = fileTableHashMap.get(fileID);
        if(fileTable != null){
            List<Long> prevSeq = fileTable.getSequence();
            List<Long> newSeq = new ArrayList<Long>();
            newSeq.add(0, prevSeq.get(0));
            if(endByte > prevSeq.get(1)) {
                newSeq.add(1, endByte);
            }
            else {
                newSeq.add(1, prevSeq.get(1));
            }

            fileTable.setSequence(newSeq);
            logger.d("DEBUG", "FileManager SET_END_SEQ: " + newSeq);
        }
    }

    public void forceSetEndSequence(String fileID, long endByte){
        FileTable fileTable = fileTableHashMap.get(fileID);
        if(fileTable != null){
            List<Long> newSeq = new ArrayList<Long>();
            newSeq.add(0, (long)0); // check this

            newSeq.add(1, endByte);

            fileTable.setSequence(newSeq);
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
            fileWriter.write(gson.toJson(fileTableHashMap));
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
            fileTableHashMap = (ConcurrentHashMap<String, FileTable>)gson.fromJson(br, ConcurrentHashMapType);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Traverse the folder and add / remove files
     */
    public void updateFromFolder(){
        // get all files in sync folder
        ArrayList<File> files = findFiles(FILES_PATH);
        //Log.d("DEBUG", "FileManaager Files in sync: " + files.toString());

        // Add file to database if not already present
        for(File file: files){
            String fileID = getFileIDFromPath(file);
            if(fileTableHashMap.get(fileID) == null){
                long fileSize = file.length();
                List seq = new ArrayList();
                seq.add(0, 0);
                seq.add(1, fileSize);
                String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
                String ttl;
                try {
                    ttl = file.getName().split("_")[1];
                    int i = Integer.parseInt(ttl);
                }
                catch(Exception e){
                    ttl = "50";
                }
                logger.d("DEBUG", ttl);
                String destination = "DB";
                double imp = -1;
                if(file.getName().startsWith("MapDisarm_Log_")){
                    imp = 1000;
                }
                if(file.getName().startsWith("IMG_")){
                    imp = 0;
                }
                enterFile(fileID, file.getName(), seq, fileSize, Integer.parseInt(ttl), timeStamp, ttl, destination, false, imp);
            }

        }

        for (String key : fileTableHashMap.keySet()) {
            FileTable fileInfo = fileTableHashMap.get(key);
            String fileName = fileInfo.getFileName();
            List<Long> seq = fileInfo.getSequence();
            if(seq.get(1) != 0){
            boolean check = new File(FILES_PATH + "/" + fileName).exists();
            if(!check){
                fileTableHashMap.remove(key);
                logger.d("DEBUG", "FileManaager Remove from DB " + fileName);

            }
            }
        }
    }

    /**
     * Returns the list of files available in sync directory
     * @param files_path    : the sync directory
     * @return              : the list of files in sync directory
     */
    private ArrayList<File> findFiles(File files_path) {
        ArrayList<File> files = new ArrayList<File>();
        File[] allFile = files_path.listFiles();
        for(File file : allFile) {
            // ignore if it is a directory
            if(!file.isDirectory()) {
                files.add(file);
            }
        }
        return files;
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


    public void removeOldGpsLogs(String fileID){


        final String newLogName = fileTableHashMap.get(fileID).getFileName();

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
                    fileTableHashMap.remove(fileid);
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

    public void updateCountOfTypes(){
        countOfTypes.clear();

        for (String fileID : fileTableHashMap.keySet()) {
            FileTable fileTable = fileTableHashMap.get(fileID);
            String fileName = fileTable.getFileName();
            if(fileName.startsWith("IMG_") && fileTable.getSequence().get(1) > 0){
                Set<String> typeSet = new HashSet<>();
                String typesString = fileName.split("_")[1];
                String[] typesArray = typesString.split("-");
                for(String typeStr:typesArray ){
                    typeSet.add(typeStr);
                }
                if(countOfTypes.get(typeSet) == null){
                    countOfTypes.put(typeSet, 0);
                }
                countOfTypes.put(typeSet, countOfTypes.get(typeSet) + 1);
            }
        }
    }

    public void addDiffuseImpOfTile(int x, int y , int z, Double ImpD){
        String tileName = "" + z + "-" + x + "-" + y + ".topojson";
        if(diffImpOfFilesInTile.get(tileName) == null) {
            diffImpOfFilesInTile.put(tileName, 0.0);
        }
        diffImpOfFilesInTile.put(tileName, (diffImpOfFilesInTile.get(tileName) + ImpD));
        //logger.d("DEBUG: ", "update importance of tile " + tileName + " " + ImpD);
        if(maxImportanceOfaTile < diffImpOfFilesInTile.get(tileName)){
            maxImportanceOfaTile = diffImpOfFilesInTile.get(tileName);
        }
    }


    public void recurseDiffuseImpOfTile(int x, int y , int z, Double ImpD, int depth){
        if(depth < 5) {
            // new importance
            ImpD = ImpD / Math.pow(10, depth);

            // calculate corner points
            int c1x = x - depth;
            int c1y = y - depth;
            addDiffuseImpOfTile(c1x, c1y, z, ImpD);

            int c2x = x + depth;
            int c2y = y - depth;
            addDiffuseImpOfTile(c2x, c2y, z, ImpD);

            int c3x = x - depth;
            int c3y = y + depth;
            addDiffuseImpOfTile(c3x, c3y, z, ImpD);

            int c4x = x + depth;
            int c4y = y + depth;
            addDiffuseImpOfTile(c4x, c4y, z, ImpD);

            // keep x-depth constant
            for (int i = (-1) * (depth - 1); i < depth; i += 1) {
                addDiffuseImpOfTile(c1x, y + i, z, ImpD);
            }
            // keep y-depth constant
            for (int i = (-1) * (depth - 1); i < depth; i += 1) {
                addDiffuseImpOfTile(x + i, c1y, z, ImpD);
            }
            // keep x+depth constant
            for (int i = (-1) * (depth - 1); i < depth; i += 1) {
                addDiffuseImpOfTile(c4x, y + i, z, ImpD);
            }
            // keep y+depth constant
            for (int i = (-1) * (depth - 1); i < depth; i += 1) {
                addDiffuseImpOfTile(x + i, c4y,  z, ImpD);
            }

            // new depth
            depth = depth + 1;
            recurseDiffuseImpOfTile(x, y, z, ImpD, depth);
        }


    }


    public  void diffuseImpOfFilesInTile(String tileName){
        // ID = importanceOfAllDataInThisTile
        Double ImpD  = impOfFilesInTile.get(tileName);
        tileName = tileName.split("\\.")[0];
        int z = Integer.parseInt(tileName.split("-")[0]);
        int x = Integer.parseInt(tileName.split("-")[1]);
        int y = Integer.parseInt(tileName.split("-")[2]);
        addDiffuseImpOfTile(x, y, z, ImpD);

        recurseDiffuseImpOfTile(x, y, z, ImpD, 1);
    }

    public void updateImportanceOfFilesAndTiles(){
        logger.d("DEBUG: ", " CALCULATING FILE IMPORTANCE");
        updateCountOfTypes();
        impOfFilesInTile.clear();
        diffImpOfFilesInTile.clear();
        int totalCountOfAllTypeSets = 0;
        maxImportanceOfaTile = 0;
        for( Set<String> typeSet:countOfTypes.keySet()){
            totalCountOfAllTypeSets = totalCountOfAllTypeSets + countOfTypes.get(typeSet);
        }

        for (String fileID : fileTableHashMap.keySet()) {
            FileTable fileTable = fileTableHashMap.get(fileID);
            String fileName = fileTable.getFileName();
            if(fileName.startsWith("IMG_") && fileTable.getSequence().get(1) != 0){
                // Calculate Rank if the file is at least partially received

                String typesString = fileName.split("_")[1];
                String[] typesArray = typesString.split("-");
                Set<String> typeSet = new HashSet<>();
                for(String typeStr:typesArray ){
                    typeSet.add(typeStr);
                }
                int countOfTypeSetInThisFile = countOfTypes.get(typeSet);

                double proportion = (double) countOfTypeSetInThisFile / (double) totalCountOfAllTypeSets;
                logger.d("ImportanceCalculator: ", "Proportion of " + typesString + " : " + proportion);

                double informationOfFile = (double)(-1) * (Math.log(proportion) / Math.log((double)2));
                logger.d("ImportanceCalculator: ", "Information of " + typesString + " : " + informationOfFile);

                Date originDate = null;

                try {
                    originDate = dateFormat.parse(fileName.split("_")[4]);
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
                    logger.d("ImportanceCalculator: ", "Importance of " + fileName + " : " + importanceValue);
                    fileTableHashMap.get(fileID).setImportance(importanceValue);
                }

                // update Count of files in Tiles
                double lat = Double.parseDouble(fileName.split("_")[2]);
                double lon = Double.parseDouble(fileName.split("_")[3]);
                String tileName = getTileXYString(lat, lon, 18);

                if(impOfFilesInTile.get(tileName) == null){
                    impOfFilesInTile.put(tileName, 0.0);
                }
                impOfFilesInTile.put(tileName, (impOfFilesInTile.get(tileName) + fileTableHashMap.get(fileID).getImportance()));

            }

        }

        for (String tileName : impOfFilesInTile.keySet()){
            diffuseImpOfFilesInTile(tileName);
        }

        // set importance of tiles
        for (String fileID : fileTableHashMap.keySet()) {
            String fileName = fileTableHashMap.get(fileID).getFileName();
            if(fileName.startsWith("18-")){

                if(diffImpOfFilesInTile.get(fileName) != null){
                    fileTableHashMap.get(fileID).setImportance( (double)(-1) + diffImpOfFilesInTile.get(fileName) / maxImportanceOfaTile);
                }
                else {
                    fileTableHashMap.get(fileID).setImportance( (double)(-1));
                }
            }
        }
    }


}





/**
 * Class to save file description
 */
class FileTable implements java.io.Serializable{
    private String fileID;
    private String fileName;
    private List<Long> sequence;
    private double fileSize;
    private int priority;
    private String timestamp;
    private String ttl;
    private String destination;
    private boolean destinationReachedStatus;
    private double importance;

    public FileTable(String fileID, String fileName, List<Long> sequence, double fileSize, int priority,
                     String timestamp, String ttl, String destination, boolean destinationReachedStatus, double importance){
        this.fileID = fileID;
        this.fileName = fileName;
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

    String getTtl(){
        return this.ttl;
    }

    String getDestination(){
        return this.destination;
    }

    boolean getDestinationReachedStatus(){
        return this.destinationReachedStatus;
    }

    double getImportance() { return  this.importance; }

    void setImportance(double imp) { this.importance = imp; }

    void setTtl(String ttl) {
        this.ttl = ttl;
    }

    void setSequence(List<Long> sequence){
        this.sequence = sequence;
    }

    void setDestinationReachedStatus(boolean status){
        this.destinationReachedStatus = status;
    }

}
