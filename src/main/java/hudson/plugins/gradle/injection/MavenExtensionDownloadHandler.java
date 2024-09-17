package hudson.plugins.gradle.injection;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.FilePath;
import hudson.Util;
import hudson.plugins.gradle.injection.extension.ExtensionClient;
import jenkins.model.Jenkins;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MavenExtensionDownloadHandler implements MavenInjectionAware {

    public static final String DOWNLOAD_CACHE_DIR = "jenkins-gradle-plugin/cache";

    public Map<MavenExtension, String> ensureExtensionsDownloaded(File root, InjectionConfig injectionConfig) throws IOException {
        if (!isInjectionDisabledGlobally(injectionConfig)) {
            Map<MavenExtension, String> extensionsDigest = new HashMap<>();
            Path parent = root.toPath().resolve(DOWNLOAD_CACHE_DIR);

            MavenExtension develocityMavenExtension = MavenExtension.getDevelocityMavenExtension(injectionConfig.getMavenExtensionVersion());

            extensionsDigest.put(develocityMavenExtension, getExtensionDigest(injectionConfig, parent, develocityMavenExtension));
            if (InjectionUtil.isValid(InjectionConfig.checkRequiredVersion(injectionConfig.getCcudExtensionVersion()))) {
                extensionsDigest.put(MavenExtension.CCUD, getExtensionDigest(injectionConfig, parent, MavenExtension.CCUD));
            }

            return extensionsDigest;
        }

        return Collections.emptyMap();
    }

    private static String getExtensionDigest(InjectionConfig injectionConfig, Path parent, MavenExtension extension) throws IOException {
        Path metadataFile = parent.resolve(extension.getDownloadMetadataFileName());
        if (Files.exists(metadataFile)) {
            String[] metadata = Files.readString(metadataFile).split(",");
            String extensionVersion = metadata[0];
            String extensionDigest = metadata[1];
            if (!extensionVersion.equals(injectionConfig.getMavenExtensionVersion())) {
                return downloadExtension(injectionConfig, parent, extension, metadataFile);
            } else {
                return extensionDigest;
            }
        } else {
            return downloadExtension(injectionConfig, parent, extension, metadataFile);
        }
    }

    private static String downloadExtension(
            InjectionConfig injectionConfig,
            Path parent,
            MavenExtension extension,
            Path metadataFile
    ) throws IOException {
        Files.createDirectories(parent);

        Path jarFile = parent.resolve(extension.getEmbeddedJarName());

        String version = extension == MavenExtension.CCUD
                ? injectionConfig.getCcudExtensionVersion()
                : injectionConfig.getMavenExtensionVersion();

        String downloadUrl = extension.createDownloadUrl(version, injectionConfig.getMavenExtensionRepositoryUrl());
        MavenExtension.RepositoryCredentials repositoryCredentials
                = getRepositoryCredentials(injectionConfig.getMavenExtensionRepositoryCredentialId());

        try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(jarFile))) {
            ExtensionClient.INSTANCE.downloadExtension(downloadUrl, repositoryCredentials, outputStream);
        }

        String digest = Util.getDigestOf(jarFile.toFile());

        Files.writeString(metadataFile, version + "," + digest);

        return digest;
    }

    private static MavenExtension.RepositoryCredentials getRepositoryCredentials(String repositoryCredentialId) {
        if (repositoryCredentialId == null) {
            return null;
        }

        List<StandardUsernamePasswordCredentials> allCredentials
                = CredentialsProvider.lookupCredentialsInItem(StandardUsernamePasswordCredentials.class, null, null);

        return allCredentials.stream()
                .filter(it -> it.getId().equals(repositoryCredentialId))
                .findFirst()
                .map(it -> new MavenExtension.RepositoryCredentials(it.getUsername(), it.getPassword().getPlainText()))
                .orElse(null);
    }

}
