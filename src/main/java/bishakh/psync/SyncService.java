package bishakh.psync;
import java.io.IOException;

public class SyncService {

    private static final String BROADCAST_IP = "192.168.43.255";
    private static String PEER_ID = "bisu";
    private static final int PORT = 4446;
    private static final int syncInterval = 5;
    private static final int maxRunningDownloads = 5;

    private static String syncDirectory = "/home/alarm/dms/sync/";
    private static String mapFileServerDirectory = "/home/alarm/dms/";
    private static String databaseAndLogDirectory = "/home/alarm/dms/";
    private static String databaseName = "fileDB.txt";

    public Logger logger;
    public WebServer webServer;
    public Discoverer discoverer;
    public FileManager fileManager;
    public FileTransporter fileTransporter;
    public Controller controller;

    public SyncService() {
        logger = new Logger(databaseAndLogDirectory, PEER_ID);
        discoverer = new Discoverer(BROADCAST_IP, PEER_ID, PORT, logger);
        fileManager = new FileManager(PEER_ID, databaseName, databaseAndLogDirectory, syncDirectory, mapFileServerDirectory, logger);
        fileTransporter = new FileTransporter(syncDirectory, logger);
        controller = new Controller(discoverer, fileManager, fileTransporter, syncInterval, maxRunningDownloads, logger, false);
        webServer = new WebServer(8080, controller, logger);
    }


    public SyncService(String inputPeerId, String baseDirectory) {
        syncDirectory = baseDirectory + "dms/sync/";
        mapFileServerDirectory = baseDirectory + "dms/";
        databaseAndLogDirectory = baseDirectory + "dms/";
        PEER_ID = inputPeerId;

        logger = new Logger(databaseAndLogDirectory, PEER_ID);
        discoverer = new Discoverer(BROADCAST_IP, PEER_ID, PORT, logger);
        fileManager = new FileManager(PEER_ID, databaseName, databaseAndLogDirectory, syncDirectory, mapFileServerDirectory, logger);
        fileTransporter = new FileTransporter(syncDirectory, logger);
        controller = new Controller(discoverer, fileManager, fileTransporter, syncInterval, maxRunningDownloads, logger, false);
        webServer = new WebServer(8080, controller, logger);
    }


    public  void start(){
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

    public static void main(String[] args) {
        System.out.println(args.length);
        if(args.length < 2){
            SyncService s = new SyncService();
            s.start();
        }
        else {
            SyncService s = new SyncService(args[0], args[1]);
            s.start();
        }
    }

}
