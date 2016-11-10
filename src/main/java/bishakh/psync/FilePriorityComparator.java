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
    public FilePriorityComparator(FileManager fileManager, Logger logger){
        this.fileManager = fileManager;
        this.logger = logger;
    }

    @Override
    public int compare(FileEntry x, FileEntry y)
    {
        if (x.getImportance() > y.getImportance())
        {
            return -1;
        }
        if (x.getImportance() < y.getImportance())
        {
            return 1;
        }
        return 0;
    }

}