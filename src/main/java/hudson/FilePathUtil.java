package hudson;

/**
 * Utility class to access protected method from FilePath
 */
public class FilePathUtil {

  public static boolean isUnix(FilePath path){
    return path.isUnix();
  }

}
