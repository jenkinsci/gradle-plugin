package hudson.plugins.gradle.injection;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Util;
import hudson.plugins.gradle.injection.download.AgentDownloadClient;
import hudson.plugins.gradle.injection.download.RequestAuthenticator;

import javax.annotation.Nullable;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static hudson.plugins.gradle.injection.InjectionUtil.DOWNLOAD_CACHE_DIR;

public class MavenExtensionDownloadHandler implements MavenInjectionAware {

    private final AgentDownloadClient downloadClient = new AgentDownloadClient();

    public Map<MavenExtension, String> ensureExtensionsDownloaded(Supplier<File> root, InjectionConfig injectionConfig) throws IOException {
        if (isInjectionDisabledGlobally(injectionConfig)) {
            return Collections.emptyMap();
        }

        Map<MavenExtension, String> extensionsDigest = new HashMap<>();
        Path cacheDir = root.get().toPath().resolve(DOWNLOAD_CACHE_DIR);

        MavenExtension develocityMavenExtension = MavenExtension.forVersion(injectionConfig.getMavenExtensionVersion());

        extensionsDigest.put(develocityMavenExtension, getOrDownloadExtensionDigest(injectionConfig, cacheDir, develocityMavenExtension));
        if (InjectionUtil.isValid(InjectionConfig.checkRequiredVersion(injectionConfig.getCcudExtensionVersion()))) {
            extensionsDigest.put(MavenExtension.CCUD, getOrDownloadExtensionDigest(injectionConfig, cacheDir, MavenExtension.CCUD));
        }

        return extensionsDigest;
    }

    public Map<MavenExtension, String> getExtensionDigests(Supplier<File> rootDir, InjectionConfig injectionConfig) throws IOException {
        if (isInjectionDisabledGlobally(injectionConfig)) {
            return Collections.emptyMap();
        }

        Map<MavenExtension, String> extensionDigests = new HashMap<>();
        Path cacheDir = rootDir.get().toPath().resolve(DOWNLOAD_CACHE_DIR);

        MavenExtension develocityMavenExtension = MavenExtension.forVersion(injectionConfig.getMavenExtensionVersion());

        getExtensionDigest(cacheDir, develocityMavenExtension).ifPresent(it -> extensionDigests.put(develocityMavenExtension, it));
        if (InjectionUtil.isValid(InjectionConfig.checkRequiredVersion(injectionConfig.getCcudExtensionVersion()))) {
            getExtensionDigest(cacheDir, MavenExtension.CCUD).ifPresent(it -> extensionDigests.put(MavenExtension.CCUD, it));
        }

        return extensionDigests;
    }

    private static Optional<String> getExtensionDigest(Path parent, MavenExtension extension) throws IOException {
        Path metadataFile = parent.resolve(extension.getDownloadMetadataFileName());
        return ArtifactMetadata.readFromFile(metadataFile)
                .map(ArtifactMetadata::digest)
                .map(ArtifactDigest::digest);
    }

    private String getOrDownloadExtensionDigest(InjectionConfig injectionConfig, Path parent, MavenExtension extension) throws IOException {
        Path metadataFile = parent.resolve(extension.getDownloadMetadataFileName());
        String version = extension == MavenExtension.CCUD
                ? injectionConfig.getCcudExtensionVersion()
                : injectionConfig.getMavenExtensionVersion();

        var cachedDigest = ArtifactMetadata.readFromFile(metadataFile)
                .filter(m -> m.isForVersion(version))
                .map(ArtifactMetadata::digest)
                .orElse(null);

        return cachedDigest != null
                ? cachedDigest.digest()
                : downloadExtension(injectionConfig, parent, extension, metadataFile, version);
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

        URI downloadUrl = extension.createDownloadUrl(version, injectionConfig.getMavenExtensionRepositoryUrl());
        RequestAuthenticator authenticator = createRequestAuthenticator(injectionConfig.getMavenExtensionRepositoryCredentialId());

        try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(jarFile))) {
            downloadClient.download(downloadUrl, authenticator, outputStream);
        }

        String digest = Util.getDigestOf(jarFile.toFile());
        var metadata = new ArtifactMetadata(version, digest);
        metadata.writeToFile(metadataFile);

        return digest;
    }

    private static RequestAuthenticator createRequestAuthenticator(@Nullable String repositoryCredentialId) {
        if (repositoryCredentialId == null) {
            return RequestAuthenticator.NONE;
        }

        List<StandardUsernamePasswordCredentials> allCredentials =
                CredentialsProvider.lookupCredentialsInItem(StandardUsernamePasswordCredentials.class, null, null);

        return allCredentials.stream()
                .filter(c -> c.getId().equals(repositoryCredentialId))
                .findFirst()
                .map(c -> RequestAuthenticator.basic(c.getUsername(), c.getPassword().getPlainText()))
                .orElse(RequestAuthenticator.NONE);
    }
}
