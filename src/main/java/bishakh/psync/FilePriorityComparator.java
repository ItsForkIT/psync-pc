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
        if(this.priorityMethod == 3){
            // PGP has highest priority ========================================================
            // both pgp keys
            if(x.getFilePath().startsWith("pgpKey") && y.getFilePath().startsWith("pgpKey")){
                return -1;
            }
            // x pgp key
            if(x.getFilePath().startsWith("pgpKey") && !y.getFilePath().startsWith("pgpKey")){
                return -1;
            }
            //y pgp key
            if(!x.getFilePath().startsWith("pgpKey") && y.getFilePath().startsWith("pgpKey")){
                return 1;
            }

            // Now rest will go according to priority ==========================================

            // one kml another diff
            if(x.getFilePath().startsWith("SurakshitKml") && y.getFilePath().startsWith("SurakshitDiff")){
                return -1;
            }
            if(x.getFilePath().startsWith("SurakshitDiff") && y.getFilePath().startsWith("SurakshitKml")){
                return 1;
            }

            // x,y both kml/diff
            if((x.getFilePath().startsWith("SurakshitKml") && y.getFilePath().startsWith("SurakshitKml")) ||
                    (x.getFilePath().startsWith("SurakshitDiff") && y.getFilePath().startsWith("SurakshitDiff"))){
                int xpriority = x.getPriority();
                int ypriority = y.getPriority();
                if(xpriority > ypriority){
                    return -1;
                }
                return 1;

            }

            // x kml
            if((x.getFilePath().startsWith("SurakshitKml") || x.getFilePath().startsWith("SurakshitDiff"))
                    && !(y.getFilePath().startsWith("SurakshitKml") || y.getFilePath().startsWith("SurakshitDiff"))){
                return -1;
            }
            return 1;
        }
        else if(this.priorityMethod == 2){
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
                return 1;
            }
            if (x.getPriority() < y.getPriority())
            {
                return -1;
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