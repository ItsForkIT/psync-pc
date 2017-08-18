package bishakh.psync;

import java.util.Comparator;
import java.util.HashMap;

/**
 * Created by bishakh on 6/10/16.
 */

/*

priorityMethod:
0: Random
1: Based on Priority
2: Based on Importance

 */
public class FilePriorityComparator implements Comparator<FileEntry>
{
    FileManager fileManager;
    Logger logger;
    int priorityMethod;
    public FilePriorityComparator(FileManager fileManager, Logger logger, int priorityMethod){
        this.fileManager = fileManager;
        this.logger = logger;
        this.priorityMethod = priorityMethod;
    }

    @Override
    public int compare(FileEntry x, FileEntry y)
    {
        if(this.priorityMethod == 2){
            if (x.getImportance() > y.getImportance())
            {
                return -1;
            }
            if (x.getImportance() < y.getImportance())
            {
                return 1;
            }
        }
        else if(this.priorityMethod == 1){
            if (x.getPriority() > y.getPriority())
            {
                return -1;
            }
            if (x.getPriority() < y.getPriority())
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