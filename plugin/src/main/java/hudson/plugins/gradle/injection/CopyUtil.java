package hudson.plugins.gradle.injection;

import hudson.FilePath;
import hudson.Util;

import java.io.IOException;
import java.io.InputStream;

public final class CopyUtil {

    private CopyUtil() {
    }

    public static void copyResourceToNode(FilePath nodePath, String resourceName) throws IOException, InterruptedException {
        doWithResource(resourceName, is -> {
            nodePath.copyFrom(is);
            return null;
        });
    }

    public static void copyDownloadedResourceToNode(FilePath controllerRootPath, FilePath nodePath, String resourceName) throws IOException, InterruptedException {
        nodePath.copyFrom(controllerRootPath.child(InjectionUtil.DOWNLOAD_CACHE_DIR).child(resourceName));
    }

    public static String unsafeResourceDigest(String resourceName) {
        try {
            return doWithResource(resourceName, Util::getDigestOf);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T doWithResource(String resourceName, CheckedFunction<InputStream, T> action) throws IOException, InterruptedException {
        try (InputStream is = CopyUtil.class.getResourceAsStream("/hudson/plugins/gradle/injection/" + resourceName)) {
            if (is == null) {
                throw new IllegalStateException("Could not find resource: " + resourceName);
            }
            return action.apply(is);
        }
    }

    @FunctionalInterface
    private interface CheckedFunction<T, R> {

        R apply(T t) throws IOException, InterruptedException;
    }
}
