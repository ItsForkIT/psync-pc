package bishakh.psync;

import java.util.Comparator;
import java.util.HashMap;

/**
 * Created by bishakh on 6/10/16.
 */
public class FilePriorityComparator implements Comparator<FileEntry>
{
    FileManager fileManager;
    Logger logger;
    boolean randomize;
    public FilePriorityComparator(FileManager fileManager, Logger logger, boolean randomize){
        this.fileManager = fileManager;
        this.logger = logger;
        this.randomize = randomize;
    }

    @Override
    public int compare(FileEntry x, FileEntry y)
    {
        if(!this.randomize){
            if (x.getImportance() > y.getImportance())
            {
                return -1;
            }
            if (x.getImportance() < y.getImportance())
            {
                return 1;
            }
        }
        else{
            if (Math.random() < 0.5)
            {
                return -1;
            }
            else
            {
                return 1;
            }
        }

        return 0;
    }

}