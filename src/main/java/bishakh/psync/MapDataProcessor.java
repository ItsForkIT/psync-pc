package bishakh.psync;

import java.io.*;

/**
 * Created by bishakh on 6/9/16.
 */
public class MapDataProcessor {
    File syncDirectory;
    File mapDirectory;

    public MapDataProcessor(File syncDirectory, String mapDirectory) throws IOException {
        this.syncDirectory = syncDirectory;
        this.mapDirectory = new File(mapDirectory);
    }



    public void generateAllTrails(){
        File appendedFile = new File(this.mapDirectory.toString() + "/allLogs.txt");

        appendedFile.delete();
        FileOutputStream logFileOS = null;
        try {
            appendedFile.createNewFile();
            logFileOS = new FileOutputStream(appendedFile, true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        File[] logFileList = this.syncDirectory.listFiles(new FilenameFilter() {
            public boolean accept(File file, String s) {
                return s.startsWith("MapDisarm_Log");
            }
        });

        for (File file : logFileList) {
            byte[] b = new byte[0];
            try {
                RandomAccessFile f = new RandomAccessFile(file, "r");
                b = new byte[(int)f.length()];
                f.read(b);
                logFileOS.write(file.getName().getBytes());
                logFileOS.write("\n".getBytes());
                logFileOS.write(b);
            } catch (IOException e) {
                e.printStackTrace();
            }


        }

        try {
            logFileOS.flush();
            logFileOS.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    public void generateAllGIS(){
        File appendedFile = new File(this.mapDirectory.toString() + "/allGIS.txt");

        appendedFile.delete();
        FileOutputStream gisFileOS = null;
        try {
            appendedFile.createNewFile();
            gisFileOS = new FileOutputStream(appendedFile, true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        File[] gisFileList = this.syncDirectory.listFiles(new FilenameFilter() {
            public boolean accept(File file, String s) {
                return s.startsWith("IMG_");
            }
        });

        for (File file : gisFileList) {
            try {
                gisFileOS.write(file.getName().getBytes());
                gisFileOS.write("\n".getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            gisFileOS.flush();
            gisFileOS.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    }
