package hudson.plugins.gradle.injection;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import hudson.FilePath;
import hudson.plugins.gradle.injection.extension.ExtensionClient;

import javax.annotation.Nullable;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static hudson.plugins.gradle.injection.CopyUtil.copyResourceToNode;
import static hudson.plugins.gradle.injection.CopyUtil.unsafeResourceDigest;

public class MavenExtensionsHandler {

    static final String LIB_DIR_PATH = "jenkins-gradle-plugin/lib";

    private final Map<MavenExtension, MavenExtensionFileHandler> fileHandlers =
            Arrays.stream(MavenExtension.values())
                    .map(MavenExtensionFileHandler::new)
                    .collect(Collectors.toMap(h -> h.extension, Function.identity()));

    public FilePath copyExtensionToAgent(MavenExtension extension, FilePath rootPath) throws IOException, InterruptedException {
        return fileHandlers.get(extension).copyExtensionToAgent(rootPath);
    }

    public FilePath downloadExtensionToAgent(
            MavenExtension extension,
            String version,
            FilePath rootPath,
            @Nullable MavenExtension.RepositoryCredentials repositoryCredentials,
            @Nullable String repositoryUrl
    ) throws IOException, InterruptedException {
        return fileHandlers.get(extension).downloadExtensionToAgent(rootPath, version, repositoryCredentials, repositoryUrl);
    }

    public void deleteExtensionFromAgent(MavenExtension extension, FilePath rootPath) throws IOException, InterruptedException {
        fileHandlers.get(extension).deleteExtensionFromAgent(rootPath);
    }

    public void deleteAllExtensionsFromAgent(FilePath rootPath) throws IOException, InterruptedException {
        rootPath.child(LIB_DIR_PATH).deleteContents();
    }

    private static final class MavenExtensionFileHandler {

        private final MavenExtension extension;
        private final Supplier<String> extensionDigest;

        MavenExtensionFileHandler(MavenExtension extension) {
            this.extension = extension;
            this.extensionDigest = Suppliers.memoize(() -> unsafeResourceDigest(extension.getEmbeddedJarName()));
        }

        /**
         * Copies the extension to the agent, if it is not already present, and returns a path to the extension
         * on the agent.
         */
        public FilePath copyExtensionToAgent(FilePath rootPath) throws IOException, InterruptedException {
            FilePath extensionLocation = getExtensionLocation(rootPath);
            if (extensionChanged(extensionLocation)) {
                copyResourceToNode(extensionLocation, extension.getEmbeddedJarName());
            }
            return extensionLocation;
        }

        /**
         * Downloads the extension to the agent from a repository and returns a path to the extension on the agent.
         */
        public FilePath downloadExtensionToAgent(
                FilePath rootPath,
                String version,
                @Nullable MavenExtension.RepositoryCredentials repositoryCredentials,
                @Nullable String repositoryUrl
        ) throws IOException, InterruptedException {
            FilePath extensionLocation = getExtensionLocation(rootPath);
            try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(extensionLocation.write())) {
                ExtensionClient.INSTANCE.downloadExtension(
                        extension.createDownloadUrl(version, repositoryUrl), repositoryCredentials, bufferedOutputStream
                );
            }

            return extensionLocation;
        }

        public void deleteExtensionFromAgent(FilePath rootPath) throws IOException, InterruptedException {
            FilePath extensionLocation = getExtensionLocation(rootPath);
            if (extensionLocation.exists()) {
                extensionLocation.delete();
            }
        }

        private FilePath getExtensionLocation(FilePath rootPath) {
            return rootPath.child(LIB_DIR_PATH).child(extension.getTargetJarName());
        }

        private boolean extensionChanged(FilePath nodePath) throws IOException, InterruptedException {
            if (!nodePath.exists()) {
                return true;
            }
            String existingFileDigest = nodePath.digest();
            return !Objects.equals(existingFileDigest, extensionDigest.get());
        }
    }


}
