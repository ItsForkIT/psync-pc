package bishakh.psync;
import com.sun.org.apache.xpath.internal.operations.Bool;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.io.*;
import java.net.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SyncService {

    private static final String BROADCAST_IP = "172.16.5.255";
    private static String PEER_ID = "offlineMcs";
    private static final int PORT = 4446;
    private static final int syncInterval = 5;
    private static final int maxRunningDownloads = 5;

    private static String syncDirectory = "/home/alarm/DMS/sync/";
    private static String mapFileServerDirectory = "/home/alarm/DMS/";
    private static String databaseAndLogDirectory = "/home/alarm/DMS/";
    private static String databaseName = "fileDB.txt";
    private static String contactFile = "contact.txt";

    public Logger logger;
    public WebServer webServer;
    public Discoverer discoverer;
    public FileManager fileManager;
    public FileTransporter fileTransporter;
    public Controller controller;
    public File nfile;

    public SyncService() {
        File nfile =new File(contactFile);
        logger = new Logger(databaseAndLogDirectory, PEER_ID);
        discoverer = new Discoverer(BROADCAST_IP, PEER_ID, PORT, logger,nfile);
        fileManager = new FileManager(PEER_ID, databaseName, databaseAndLogDirectory, syncDirectory, mapFileServerDirectory, logger);
        fileTransporter = new FileTransporter(syncDirectory, logger);
        controller = new Controller(discoverer, fileManager, fileTransporter, syncInterval, maxRunningDownloads, logger, 2, true);
        webServer = new WebServer(8080, controller, logger);

    }


    public SyncService(String inputPeerId, String baseDirectory) {
        syncDirectory = baseDirectory + File.separator + "sync" + File.separator;
        mapFileServerDirectory = baseDirectory;
        databaseAndLogDirectory = baseDirectory;
        PEER_ID = inputPeerId;
        File nfile =new File(contactFile);
        logger = new Logger(databaseAndLogDirectory, PEER_ID);
        discoverer = new Discoverer(BROADCAST_IP, PEER_ID, PORT, logger,nfile);
        fileManager = new FileManager(PEER_ID, databaseName, databaseAndLogDirectory, syncDirectory, mapFileServerDirectory, logger);
        fileTransporter = new FileTransporter(syncDirectory, logger);
        controller = new Controller(discoverer, fileManager, fileTransporter, syncInterval, maxRunningDownloads, logger, 2, true);
        webServer = new WebServer(8080, controller, logger);
    }

    public SyncService(String inputPeerId, String baseDirectory, int priorityMethod) {
        syncDirectory = baseDirectory + File.separator + "sync" + File.separator;
        mapFileServerDirectory = baseDirectory;
        databaseAndLogDirectory = baseDirectory;
        PEER_ID = inputPeerId;

        File nfile =new File(contactFile);
        logger = new Logger(databaseAndLogDirectory, PEER_ID);
        discoverer = new Discoverer(BROADCAST_IP, PEER_ID, PORT, logger,nfile);
        fileManager = new FileManager(PEER_ID, databaseName, databaseAndLogDirectory, syncDirectory, mapFileServerDirectory, logger);
        fileTransporter = new FileTransporter(syncDirectory, logger);
        controller = new Controller(discoverer, fileManager, fileTransporter, syncInterval, maxRunningDownloads, logger, priorityMethod, true);
        webServer = new WebServer(8080, controller, logger);
    }

    public SyncService(String inputPeerId, String baseDirectory, int priorityMethod, boolean restrictedEpidemicFlag) {
        syncDirectory = baseDirectory + File.separator + "sync" + File.separator;
        mapFileServerDirectory = baseDirectory;
        databaseAndLogDirectory = baseDirectory;
        PEER_ID = inputPeerId;
        File nfile =new File(contactFile);
        logger = new Logger(databaseAndLogDirectory, PEER_ID);
        discoverer = new Discoverer(BROADCAST_IP, PEER_ID, PORT, logger,nfile);
        fileManager = new FileManager(PEER_ID, databaseName, databaseAndLogDirectory, syncDirectory, mapFileServerDirectory, logger);
        fileTransporter = new FileTransporter(syncDirectory, logger);
        controller = new Controller(discoverer, fileManager, fileTransporter, syncInterval, maxRunningDownloads, logger, priorityMethod, restrictedEpidemicFlag);
        webServer = new WebServer(8080, controller, logger);

    }



    public  void start(){

        
        FileReader fileReader =  new FileReader(nfile);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        try {
            String line =bufferedReader.readLine();
            while(line!=null)
            {
                String[] one=line.split(" ",100); /// here length of PEER_ID is restricted
                discoverer.mp.put(one[0],one[1]);
                line=bufferedReader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        PrintWriter writer = new PrintWriter(nfile);
        writer.print("");
        writer.close();
        discoverer.startDiscoverer();
        fileManager.startFileManager();
        controller.startController();
        try {
            webServer.start();
        } catch(IOException ioe) {
            logger.d("Httpd", "The server could not start.");
        }
    }

    public void stop() {
        discoverer.stopDiscoverer();
        fileManager.stopFileManager();
        controller.stopController();
        webServer.stop();
    }

    public static void main(final String[] args) {
        System.out.println(args.length);
        if(args.length < 2){
            SyncService s = new SyncService();
            s.start();
        }
        else if(args.length < 3){
            SyncService s = new SyncService(args[0], args[1]);
            s.start();
        }
        else if(args.length < 4){
            SyncService s = new SyncService(args[0], args[1], Integer.parseInt(args[2]));
            s.start();
        }
        else {
            SyncService s = new SyncService(args[0], args[1], Integer.parseInt(args[2]), Boolean.parseBoolean(args[3]));
            s.start();
        }

        // Save crash logs in a file every time the application crashes
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                Calendar cal = Calendar.getInstance();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                File crashLogFile;
                if(args.length < 2){
                    crashLogFile = new File("/home/alarm/" + "DMS/PSYNC_CrashLog");
                }
                else {
                    crashLogFile = new File(args[1] + "PSYNC_CrashLog");
                }
                if (!crashLogFile.exists()){
                    crashLogFile.mkdir();
                }
                String filename = crashLogFile + "/" + sdf.format(cal.getTime())+".txt";

                PrintStream writer;
                try {
                    writer = new PrintStream(filename, "UTF-8");
                    writer.println(e.getClass() + ": " + e.getMessage());
                    for (int i = 0; i < e.getStackTrace().length; i++) {
                        writer.println(e.getStackTrace()[i].toString());
                    }
                    System.exit(1);
                } catch (FileNotFoundException | UnsupportedEncodingException e1) {
                    e1.printStackTrace();
                }
            }
        });


    }

}
