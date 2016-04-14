package bishakh.psync;
import java.io.IOException;

public class SyncService {

    private static final String BROADCAST_IP = "192.168.43.255";
    private static final int PORT = 4446;
    private static final int syncInterval = 5;
    private static final int maxRunningDownloads = 5;

    private static String syncDirectory = "/home/bishakh/psync/sync/";
    private static String databaseDirectory = "/home/bishakh/psync/database/";
    private static String databaseName = "fileDB.txt";

    public WebServer webServer;
    public Discoverer discoverer;
    public FileManager fileManager;
    public FileTransporter fileTransporter;
    public Controller controller;

    public SyncService() {
        discoverer = new Discoverer(BROADCAST_IP, PORT);
        fileManager = new FileManager(databaseName, databaseDirectory, syncDirectory);
        fileTransporter = new FileTransporter(syncDirectory);
        controller = new Controller(discoverer, fileManager, fileTransporter, syncInterval, maxRunningDownloads);
        webServer = new WebServer(8080, controller);
    }




    public  void start(){
        discoverer.startDiscoverer();
        fileManager.startFileManager();
        controller.startController();
        try {
            webServer.start();
        } catch(IOException ioe) {
            Log.d("Httpd", "The server could not start.");
        }
    }

    public void stop() {
        discoverer.stopDiscoverer();
        fileManager.stopFileManager();
        controller.stopController();
        webServer.stop();
    }

    public static void main(String[] args) {
        SyncService s = new SyncService();
        s.start();
    }

}
