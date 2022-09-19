package hudson.plugins.gradle.injection;

import hudson.FilePath;

import java.io.IOException;
import java.io.InputStream;

public final class CopyUtil {

    private CopyUtil() {
    }

    public static void copyResourceToNode(FilePath nodePath, String resourceName) throws IOException, InterruptedException {
        try (InputStream libIs = CopyUtil.class.getResourceAsStream("/hudson/plugins/gradle/injection/" + resourceName)) {
            if (libIs == null) {
                throw new IllegalStateException("Could not find resource: " + resourceName);
            }
            nodePath.copyFrom(libIs);
        }
    }
}
