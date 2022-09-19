package hudson.plugins.gradle.injection;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import hudson.FilePath;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static hudson.plugins.gradle.injection.CopyUtil.copyResourceToNode;
import static hudson.plugins.gradle.injection.MavenExtensionsHandler.MavenExtension.CCUD;
import static hudson.plugins.gradle.injection.MavenExtensionsHandler.MavenExtension.GRADLE_ENTERPRISE;

public class MavenExtensionsHandler {

    static final String LIB_DIR_PATH = "jenkins-gradle-plugin/lib";

    private final MavenExtensionFileHandler geExtensionHandler = new MavenExtensionFileHandler(GRADLE_ENTERPRISE);
    private final MavenExtensionFileHandler ccudExtensionHandler = new MavenExtensionFileHandler(CCUD);

    public void copyGradleEnterpriseExtensionToAgent(FilePath rootPath) throws IOException, InterruptedException {
        geExtensionHandler.copyExtensionToAgent(rootPath);
    }

    public FilePath getGradleEnterpriseExtensionPath(FilePath rootPath) throws IOException {
        return geExtensionHandler.getAgentExtensionPath(rootPath);
    }

    public void copyCCUDExtensionToAgent(FilePath rootPath) throws IOException, InterruptedException {
        ccudExtensionHandler.copyExtensionToAgent(rootPath);
    }

    public FilePath getCCUDExtensionPath(FilePath rootPath) throws IOException {
        return ccudExtensionHandler.getAgentExtensionPath(rootPath);
    }

    public void deleteAllExtensionsFromAgent(FilePath rootPath) throws IOException, InterruptedException {
        rootPath.child(LIB_DIR_PATH).deleteContents();
    }

    private static final class MavenExtensionFileHandler {
        private final MavenExtension extension;

        MavenExtensionFileHandler(MavenExtension extension) {
            this.extension = extension;
        }

        public void copyExtensionToAgent(FilePath rootPath) throws IOException, InterruptedException {
            copyResourceToNode(rootPath.child(LIB_DIR_PATH).child(extension.getJarName()), extension.getJarName());
        }

        public FilePath getAgentExtensionPath(FilePath rootPath) {
            return rootPath.child(LIB_DIR_PATH).child(extension.getJarName());
        }
    }

    public enum MavenExtension {
        GRADLE_ENTERPRISE("gradle-enterprise-maven-extension"),
        CCUD("common-custom-user-data-maven-extension");

        final String name;
        final Supplier<String> version;

        MavenExtension(String name) {
            this.name = name;
            this.version = Suppliers.memoize(this::getExtensionVersion);
        }

        public String getJarName() {
            return name + "-" + version.get() + ".jar";
        }

        private String getExtensionVersion() {
            try {
                String resourceName = name + "-version.txt";
                try (InputStream version = MavenBuildScanInjection.class.getResourceAsStream("/versions/" + resourceName)) {
                    if (version == null) {
                        throw new IllegalStateException("Could not find resource: " + resourceName);
                    }
                    return IOUtils.toString(version, StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
