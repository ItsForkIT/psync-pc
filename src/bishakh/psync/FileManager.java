package bishakh.psync;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File Table and File List Format:
 * ---------------------------------------------------------------------------------------------------------------------
 * |  File ID  |  File Name  |  Sequence  |  File Size  | Priority | Timestamp | TTL | Destination | DestReachedStatus |
 * ---------------------------------------------------------------------------------------------------------------------
 */
public class FileManager {

    ConcurrentHashMap<String, FileTable> fileTableHashMap = new ConcurrentHashMap<String, FileTable>();
    Type ConcurrentHashMapType = new TypeToken<ConcurrentHashMap<String, FileTable>>(){}.getType();
    Gson gson = new Gson();
    final String DATABASE_NAME;
    final String DATABASE_PATH;
    final File FILES_PATH;
    Logger logger;
    private FileManagerThread fileManagerThread = new FileManagerThread();
    Long chunkSize = Long.valueOf(1048576); // 1 MB in bytes

    public FileManager(String databaseName, String databaseDirectory, String syncDirectory, Logger loggerObj){
        this.DATABASE_NAME = databaseName;
        this.DATABASE_PATH = databaseDirectory + DATABASE_NAME;
        this.FILES_PATH = new File(syncDirectory);
        this.logger = loggerObj;
        logger.d("DEBUG", " Starting FileManager with directories: " + this.FILES_PATH + ", " + this.DATABASE_PATH);
        readDB();
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
            try {
                logger.d("DEBUG", "FileManager Thread started");
                this.exit = false;
                this.isRunning = true;
                while(!this.exit) {
                    logger.d("DEBUG", "FileManager Scanning..");
                    updateFromFolder();
                    writeDB();
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
    private void enterFile(String fileID, String fileName, ConcurrentHashMap<Integer, List<Long>> sequence,
                           double fileSize, int priority, String timestamp, String ttl, String destination,
                           boolean destinationReachedStatus, List<Integer> chunkAvailable){
        FileTable newFileInfo = new FileTable( fileID, fileName, sequence, fileSize, priority, timestamp,
                ttl, destination, destinationReachedStatus, chunkAvailable);
        fileTableHashMap.put( fileID, newFileInfo);
        logger.d("DEBUG", "FileManager Add to DB: " + fileName);
    }

    public void setEndSequence(String fileID, int chunkNumber, long endByte){
        FileTable fileTable = fileTableHashMap.get(fileID);
        if(fileTable != null){
            List<Long> prevSeqList = fileTable.getSequence().get(chunkNumber);
            List<Long> newSeqList = new ArrayList<Long>();
            newSeqList.add(0, prevSeqList.get(0));
            if(endByte > prevSeqList.get(1)) {
                newSeqList.add(1, endByte);
            }
            else {
                newSeqList.add(1, prevSeqList.get(1));
            }

            // replace the old sequence with the new one
            ConcurrentHashMap<Integer, List<Long>> newSeq = fileTable.getSequence();
            newSeq.put( chunkNumber, newSeqList);
            fileTable.setSequence( newSeq);
            logger.d("DEBUG", "FileManager SET_END_SEQ: " + newSeq);
        }
    }

    public void forceSetEndSequence(String fileID, int chunkNumber, long endByte){
        FileTable fileTable = fileTableHashMap.get(fileID);
        if(fileTable != null){
            List<Long> newSeqList = new ArrayList<Long>();
            newSeqList.add(0, (long)0); // check this

            newSeqList.add(1, endByte);

            // replace the old sequence with the new one
            ConcurrentHashMap<Integer, List<Long>> newSeq = fileTable.getSequence();
            newSeq.put( chunkNumber, newSeqList);
            fileTable.setSequence( newSeq);
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

            /*
            FileOutputStream fileOutputStream = new FileOutputStream(DATABASE_PATH);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(fileTableHashMap);
            objectOutputStream.close();
            fileOutputStream.close();
            */

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
            /*
            FileInputStream fileInputStream = new FileInputStream(DATABASE_PATH);
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
            fileTableHashMap = (ConcurrentHashMap<String, FileTable>) objectInputStream.readObject();
            objectInputStream.close();
            fileInputStream.close();
            */
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } /*catch (StreamCorruptedException e) {
            e.printStackTrace();
        }*/ /*catch (IOException e) {
            e.printStackTrace();
        }*/ /*catch (ClassNotFoundException e) {
            e.printStackTrace();
        }*/
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
                
                ConcurrentHashMap<Integer, List<Long>> sequence = new ConcurrentHashMap<>();

                /*
                Find out the number of chunks required for the file
                Map them as present
                 */
                List<Integer> chunkAvailable = new ArrayList<>();
                long temp = fileSize;
                int pos = 0;
                while (temp > 0) {
                    chunkAvailable.add( pos, 1);
                    temp = temp - chunkSize;
                    pos++;
                }
                /*
                Set sequence of the file
                 */
                long startByte = 0;
                int i;
                for (i = 0; i < pos-1; i++) {
                    List<Long> sequenceList = new ArrayList<>();
                    sequenceList.add(0, startByte);
                    sequenceList.add(1, startByte + chunkSize);
                    sequence.put(i,sequenceList);
                    startByte = startByte + chunkSize;
                }
                List<Long> sequenceList = new ArrayList<>();
                sequenceList.add(0, startByte);
                sequenceList.add(1, fileSize);
                sequence.put(i, sequenceList);

                String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
                String ttl;
                try {
                    ttl = file.getName().split("_")[1];
                }
                catch(Exception e) {
                    ttl = "50";
                }
                logger.d("DEBUG", ttl);
                String destination = "DB";
                enterFile(fileID, file.getName(), sequence, fileSize, Integer.parseInt(ttl), timeStamp,
                        ttl, destination, false, chunkAvailable);
            }
        }

        for (String key : fileTableHashMap.keySet()) {
            FileTable fileInfo = fileTableHashMap.get(key);
            String fileName = fileInfo.getFileName();
            boolean check = new File(FILES_PATH + "/" + fileName).exists();
            if(!check){
                fileTableHashMap.remove(key);
                logger.d("DEBUG", "FileManaager Remove from DB " + fileName);

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


}


/**
 * Class to save file description
 */
class FileTable implements java.io.Serializable{
    private String fileID;
    private String fileName;
    private ConcurrentHashMap<Integer, List<Long>> sequence;
    private double fileSize;
    private int priority;
    private String timestamp;
    private String ttl;
    private String destination;
    private boolean destinationReachedStatus;
    private List<Integer> chunkAvailable;

    public FileTable(String fileID, String fileName, ConcurrentHashMap<Integer, List<Long>> sequence,
                     double fileSize, int priority, String timestamp, String ttl, String destination,
                     boolean destinationReachedStatus,
                     List<Integer> chunkAvailable){
        this.fileID = fileID;
        this.fileName = fileName;
        this.sequence = sequence;
        this.fileSize = fileSize;
        this.priority = priority;
        this.timestamp = timestamp;
        this.ttl = ttl;
        this.destination = destination;
        this.destinationReachedStatus = destinationReachedStatus;
        this.chunkAvailable = chunkAvailable;
    }

    String getFileID(){
        return this.fileID;
    }

    String getFileName(){
        return this.fileName;
    }

    ConcurrentHashMap<Integer, List<Long>> getSequence() {
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

    List<Integer> getChunkAvailable() { return this.chunkAvailable; }

    void setTtl(String ttl) {
        this.ttl = ttl;
    }

    void setSequence(ConcurrentHashMap<Integer, List<Long>> sequence){
        this.sequence = sequence;
    }

    void setDestinationReachedStatus(boolean status){
        this.destinationReachedStatus = status;
    }
}
