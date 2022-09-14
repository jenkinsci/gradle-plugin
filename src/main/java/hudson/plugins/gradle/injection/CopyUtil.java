package hudson.plugins.gradle.injection;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.FilePath;

import java.io.IOException;
import java.io.InputStream;

public class CopyUtil {

    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
    public static void copyResourceToNode(FilePath nodePath, String resourceName) throws IOException, InterruptedException {
        try (InputStream libIs = CopyUtil.class.getResourceAsStream("/hudson/plugins/gradle/injection/" + resourceName)) {
            if (libIs == null) {
                throw new IllegalStateException("Could not find resource: " + resourceName);
            }
            nodePath.copyFrom(libIs);
        }
    }

}
