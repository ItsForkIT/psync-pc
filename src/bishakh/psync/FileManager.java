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
    private FileManagerThread fileManagerThread = new FileManagerThread();

    public FileManager(String databaseName, String databaseDirectory, String syncDirectory){
        this.DATABASE_NAME = databaseName;
        this.DATABASE_PATH = databaseDirectory + DATABASE_NAME;
        this.FILES_PATH = new File(syncDirectory);
        Log.d("DEBUG", " Starting FileManager with directories: " + this.FILES_PATH + ", " + this.DATABASE_PATH);
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
                Log.d("DEBUG", "FileManager Thread started");
                this.exit = false;
                this.isRunning = true;
                while(!this.exit) {
                    Log.d("DEBUG", "FileManager Scanning..");
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

            Log.d("DEBUG", "FileManager Thread Stopped");
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
                          String timestamp, String ttl, String destination, boolean destinationReachedStatus){
        FileTable newFileInfo = new FileTable( fileID, fileName, sequence, fileSize, priority, timestamp,
                ttl, destination, destinationReachedStatus);
        fileTableHashMap.put( fileID, newFileInfo);
        Log.d("DEBUG", "FileManager Add to DB: " + fileName);
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
            Log.d("DEBUG", "FileManager SET_END_SEQ: " + newSeq);
        }
    }

    public void forceSetEndSequence(String fileID, long endByte){
        FileTable fileTable = fileTableHashMap.get(fileID);
        if(fileTable != null){
            List<Long> newSeq = new ArrayList<Long>();
            newSeq.add(0, (long)0); // check this

            newSeq.add(1, endByte);

            fileTable.setSequence(newSeq);
            Log.d("DEBUG", "FileManager SET_END_SEQ: " + newSeq);
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
        Log.d("DEBUG", "FileManager reading from fileDB");
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
                List seq = new ArrayList();
                seq.add(0, 0);
                seq.add(1, fileSize);
                String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
                String ttl = "NONE";
                String destination = "DB";
                enterFile(fileID, file.getName(), seq, fileSize, 1, timeStamp, ttl, destination, false);
            }

        }

        for (String key : fileTableHashMap.keySet()) {
            FileTable fileInfo = fileTableHashMap.get(key);
            String fileName = fileInfo.getFileName();
            boolean check = new File(FILES_PATH + "/" + fileName).exists();
            if(!check){
                fileTableHashMap.remove(key);
                Log.d("DEBUG", "FileManaager Remove from DB " + fileName);

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
    private List<Long> sequence;
    private double fileSize;
    private int priority;
    private String timestamp;
    private String ttl;
    private String destination;
    private boolean destinationReachedStatus;

    public FileTable(String fileID, String fileName, List<Long> sequence, double fileSize, int priority,
                     String timestamp, String ttl, String destination, boolean destinationReachedStatus){
        this.fileID = fileID;
        this.fileName = fileName;
        this.sequence = sequence;
        this.fileSize = fileSize;
        this.priority = priority;
        this.timestamp = timestamp;
        this.ttl = ttl;
        this.destination = destination;
        this.destinationReachedStatus = destinationReachedStatus;
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
