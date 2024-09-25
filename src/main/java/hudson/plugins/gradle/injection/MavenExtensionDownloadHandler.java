package hudson.plugins.gradle.injection;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Util;
import hudson.model.Item;
import hudson.plugins.gradle.injection.extension.ExtensionClient;
import org.apache.commons.io.IOUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class MavenExtensionDownloadHandler implements MavenInjectionAware {

    public static final String DOWNLOAD_CACHE_DIR = "jenkins-gradle-plugin/cache";

    private final ExtensionClient extensionClient = new ExtensionClient();

    public Map<MavenExtension, String> ensureExtensionsDownloaded(Supplier<File> root, InjectionConfig injectionConfig) throws IOException {
        if (!isInjectionDisabledGlobally(injectionConfig)) {
            Map<MavenExtension, String> extensionsDigest = new HashMap<>();
            Path cacheDir = root.get().toPath().resolve(DOWNLOAD_CACHE_DIR);

            MavenExtension develocityMavenExtension = MavenExtension.getDevelocityMavenExtension(injectionConfig.getMavenExtensionVersion());

            extensionsDigest.put(develocityMavenExtension, getOrDownloadExtensionDigest(injectionConfig, cacheDir, develocityMavenExtension));
            if (InjectionUtil.isValid(InjectionConfig.checkRequiredVersion(injectionConfig.getCcudExtensionVersion()))) {
                extensionsDigest.put(MavenExtension.CCUD, getOrDownloadExtensionDigest(injectionConfig, cacheDir, MavenExtension.CCUD));
            }

            return extensionsDigest;
        }

        return Collections.emptyMap();
    }

    public Map<MavenExtension, String> getExtensionDigests(Supplier<File> rootDir, InjectionConfig injectionConfig) throws IOException {
        if (!isInjectionDisabledGlobally(injectionConfig)) {
            Map<MavenExtension, String> extensionDigests = new HashMap<>();
            Path cacheDir = rootDir.get().toPath().resolve(DOWNLOAD_CACHE_DIR);

            MavenExtension develocityMavenExtension = MavenExtension.getDevelocityMavenExtension(injectionConfig.getMavenExtensionVersion());

            getExtensionDigest(cacheDir, develocityMavenExtension).ifPresent(it -> extensionDigests.put(develocityMavenExtension, it));
            if (InjectionUtil.isValid(InjectionConfig.checkRequiredVersion(injectionConfig.getCcudExtensionVersion()))) {
                getExtensionDigest(cacheDir, MavenExtension.CCUD).ifPresent(it -> extensionDigests.put(MavenExtension.CCUD, it));
            }

            return extensionDigests;
        }

        return Collections.emptyMap();
    }

    private static Optional<String> getExtensionDigest(Path parent, MavenExtension extension) throws IOException {
        Path metadataFile = parent.resolve(extension.getDownloadMetadataFileName());
        if (Files.exists(metadataFile)) {
            String[] metadata = new String(Files.readAllBytes(metadataFile), StandardCharsets.UTF_8).split(",");

            return Optional.of(metadata[1]);
        }

        return Optional.empty();
    }

    private String getOrDownloadExtensionDigest(InjectionConfig injectionConfig, Path parent, MavenExtension extension) throws IOException {
        Path metadataFile = parent.resolve(extension.getDownloadMetadataFileName());
        String version = extension == MavenExtension.CCUD
                ? injectionConfig.getCcudExtensionVersion()
                : injectionConfig.getMavenExtensionVersion();

        if (Files.exists(metadataFile)) {
            String[] metadata = new String(Files.readAllBytes(metadataFile), StandardCharsets.UTF_8).split(",");
            String extensionVersion = metadata[0];
            String extensionDigest = metadata[1];

            if (!extensionVersion.equals(version)) {
                return downloadExtension(injectionConfig, parent, extension, metadataFile, version);
            } else {
                return extensionDigest;
            }
        } else {
            return downloadExtension(injectionConfig, parent, extension, metadataFile, version);
        }
    }

    private String downloadExtension(
            InjectionConfig injectionConfig,
            Path parent,
            MavenExtension extension,
            Path metadataFile,
            String version
    ) throws IOException {
        Files.createDirectories(parent);

        Path jarFile = parent.resolve(extension.getEmbeddedJarName());

        String downloadUrl = extension.createDownloadUrl(version, injectionConfig.getMavenExtensionRepositoryUrl());
        MavenExtension.RepositoryCredentials repositoryCredentials
                = getRepositoryCredentials(injectionConfig.getMavenExtensionRepositoryCredentialId());

        try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(jarFile))) {
            extensionClient.downloadExtension(downloadUrl, repositoryCredentials, outputStream);
        }

        String digest = Util.getDigestOf(jarFile.toFile());

        Files.write(metadataFile, (version + "," + digest).getBytes(StandardCharsets.UTF_8));

        return digest;
    }

    private static MavenExtension.RepositoryCredentials getRepositoryCredentials(String repositoryCredentialId) {
        if (repositoryCredentialId == null) {
            return null;
        }

        List<StandardUsernamePasswordCredentials> allCredentials
                = CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, (Item) null, null, Collections.emptyList());

        return allCredentials.stream()
                .filter(it -> it.getId().equals(repositoryCredentialId))
                .findFirst()
                .map(it -> new MavenExtension.RepositoryCredentials(it.getUsername(), it.getPassword().getPlainText()))
                .orElse(null);
    }
}
