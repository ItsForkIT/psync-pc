package bishakh.psync;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The File Transporter module : request a file from a peer node
 */
public class FileTransporter {
    Gson gson = new Gson();
    public ConcurrentHashMap<Thread, ResumeDownloadThread> ongoingDownloadThreads = new ConcurrentHashMap<Thread, ResumeDownloadThread>();
    Type ConcurrentHashMapType = new TypeToken<ConcurrentHashMap<String, FileEntry>>(){}.getType();
    String syncDirectory;
    Logger logger;

    public FileTransporter(String syncDirectory, Logger LoggerObj){
        this.syncDirectory = syncDirectory;
        this.logger = LoggerObj;
    }


    public void downloadFile(String fileID, String fileName, String filePath, String peerIP, String peerID, long startByte, long endByte, double fileSize) throws MalformedURLException {
        File f = new File(syncDirectory + "/" + filePath);
        File parent = f.getParentFile();
        if(!parent.exists() && !parent.mkdirs()){
            throw new IllegalStateException("Couldn't create dir: " + parent);
        }
        URL fileUrl = new URL("http://"+ peerIP +":8080/getFile/" + fileID);
        ResumeDownloadThread resumeDownloadThread = new ResumeDownloadThread(fileUrl , fileID, fileName, f, startByte, endByte, fileSize, peerID);
        Thread t = new Thread(resumeDownloadThread);
        ongoingDownloadThreads.put(t, resumeDownloadThread);
        logger.d("DEBUG:", "MISSING FILE DOWNLOAD START START BYTE = " + startByte + " END BYTE = " + endByte);
        t.start();
    }


    public class ResumeDownloadThread implements Runnable {
        URL url;
        File outputFile;
        long startByte, endByte;
        final int BUFFER_SIZE = 10240;

        public String fileID;
        public  String fileName;
        boolean mIsFinished = false;
        boolean DOWNLOADING = true;
        boolean mState = true;
        long presentByte;
        public boolean isRunning = false;
        double filesize;
        String peerId;

        public ResumeDownloadThread(URL url, String fileID, String filename, File outputFile, long startByte, long endByte, double fileSize, String peerID){
            this.url = url;
            this.outputFile = outputFile;
            this.startByte = startByte;
            this.endByte = endByte;
            this.isRunning = false;
            this.presentByte = startByte;
            this.fileID = fileID;
            this.filesize = fileSize;
            this.peerId = peerID;
            this.fileName = filename;
        }

        @Override
        public void run() {
            this.isRunning = true;
            BufferedInputStream in = null;
            RandomAccessFile raf = null;
            int response = 0;

            try {
                String byteRange;
                isRunning = true;
                // open Http connection to URL
                HttpURLConnection connection = (HttpURLConnection)url.openConnection();
                logger.d("DEBUG:FILE TRANSPORTER", "URl is" + url);


                // set the range of byte to download
                if(endByte < 0) {
                    byteRange = startByte + "-" /*+ endByte*/;
                }
                else {
                    if(endByte < startByte){
                        throw new IllegalArgumentException();
                    }
                    byteRange = startByte + "-" + endByte;
                }
                connection.setRequestProperty("Range", "bytes=" + byteRange);
                connection.setRequestMethod("GET");
                connection.setConnectTimeout( 5*1000);
                connection.setReadTimeout(5*1000);

                logger.d("DEBUG:FILE TRANSPORTER", "Connection created" + byteRange);

                // connect to server
                connection.connect();
                logger.d("DEBUG:FILE TRANSPORTER", "Callled connect with timeout " + connection.getConnectTimeout());
                logger.d("DEBUG:FILE TRANSPORTER", ""+connection.getResponseCode());

                // Make sure the response code is in the 200 range.
                if (connection.getResponseCode() / 100 != 2) {
                    logger.d("DEBUG:FILE TRANSPORTER", "error : Response code out of 200 range");
                }

                else {
                    response = 200;
                    logger.d("DEBUG:FILE TRANSPORTER", "Response code : " + connection.getResponseCode());
                    // get the input stream
                    in = new BufferedInputStream(connection.getInputStream());

                    // open the output file and seek to the start location
                    raf = new RandomAccessFile(outputFile, "rw");
                    raf.seek(startByte);
                    logger.write("START_FILE_DOWNLOAD, " + fileID + ", " + fileName + ", " + startByte + ", " + this.filesize + ", " + this.peerId);
                    byte data[] = new byte[BUFFER_SIZE];
                    int numBytesRead;
                    while (/*(mState == DOWNLOADING) &&*/ ((numBytesRead = in.read(data, 0, BUFFER_SIZE)) != -1)) {
                        // write to buffer
                        raf.write(data, 0, numBytesRead);
                        this.presentByte = this.presentByte + numBytesRead;
                        // increase the startByte for resume later
                        startByte += numBytesRead;
                        // increase the downloaded size
                        //Log.d("DEBUG:FILE TRANSPORTER", "Fetching  data " + startByte);
                    }

                    if (mState == DOWNLOADING) {
                        mIsFinished = true;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                logger.d("DEBUG:FILE TRANSPORTER", "Connection not established" + e);
            } finally {
                isRunning = false;
                if (raf != null) {
                    try {
                        raf.close();
                    } catch (IOException e) {}
                }

                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {}
                }
                if(response == 200) {
                    logger.write("STOP_FILE_DOWNLOAD, " + fileID + ", " + fileName + ", " + this.presentByte + ", " + this.filesize + ", " + this.peerId);
                }
                this.isRunning = false;
            }
        }

        public long getPresentByte(){
            return this.presentByte;
        }
    }

    /**
     * Thread to fetch the list of files from a peer
     */
    class ListFetcher implements Runnable {
        URL url;
        String peerAddress;
        final int BUFFER_SIZE = 10240;
        Controller controller;

        boolean mIsFinished = false;
        boolean DOWNLOADING = true;
        boolean mState = true;

        public ListFetcher(Controller controller, URL url, String peerAddress){
            this.url = url;
            this.peerAddress = peerAddress;
            this.controller = controller;
        }

        @Override
        public void run() {
            BufferedInputStream in = null;

            try {
                // open Http connection to URL
                HttpURLConnection connection = (HttpURLConnection)url.openConnection();
                logger.d("DEBUG:FILE TRANSPORTER", "URl is" + url);


                // set the range of byte to download

                String byteRange = "0-";

                //conn.setRequestProperty("Range", "bytes=" + byteRange);
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5 * 1000);
                connection.setReadTimeout(5 * 1000);

                logger.d("DEBUG:FILE TRANSPORTER", "Connection created" + byteRange);

                // connect to server
                connection.connect();
                logger.d("DEBUG:FILE TRANSPORTER", "Callled connect with timeout " + connection.getConnectTimeout());
                logger.d("DEBUG:FILE TRANSPORTER", "" + connection.getResponseCode());

                // Make sure the response code is in the 200 range.
                if (connection.getResponseCode() / 100 != 2) {
                    logger.d("DEBUG:FILE TRANSPORTER", "error : Response code out of 200 range");
                }

                logger.d("DEBUG:FILE TRANSPORTER", "Response code : " + connection.getResponseCode());
                // get the input stream
                in = new BufferedInputStream(connection.getInputStream());
                BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                FileTable fileTable;
                fileTable = (FileTable) gson.fromJson(br, FileTable.class);
                controller.peerFilesFetched(peerAddress, fileTable);
                logger.write("SUMMARY_VECTOR_RECEIVED, " + connection.getContentLength());
                //Log.d("DEBUG:FILE TRANSPORTER", "List Json: " + gson.toJson(fileTableHashMap).toString());


            } catch (Exception e) {
                e.printStackTrace();
                logger.d("DEBUG:FILE TRANSPORTER", "Connection not established" + e);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {}
                }
            }
        }
    }
}
