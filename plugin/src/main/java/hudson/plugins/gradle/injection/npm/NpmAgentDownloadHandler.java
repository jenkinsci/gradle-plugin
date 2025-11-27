package hudson.plugins.gradle.injection.npm;

import hudson.Util;
import hudson.plugins.gradle.injection.ArtifactDigest;
import hudson.plugins.gradle.injection.ArtifactMetadata;
import hudson.plugins.gradle.injection.InjectionConfig;
import hudson.plugins.gradle.injection.InjectionUtil;
import hudson.plugins.gradle.injection.download.AgentDownloadClient;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public class NpmAgentDownloadHandler {

    private static final String FILENAME = "develocity-npm-agent";
    private static final String METADATA_FILENAME = FILENAME + ".meta";
    private static final String DEFAULT_REGISTRY_URL = "https://registry.npmjs.org";

    public static final String AGENT_FILENAME = FILENAME + ".tgz";

    private final AgentDownloadClient downloadClient = new AgentDownloadClient();

    public ArtifactDigest downloadNpmAgent(Supplier<File> root, InjectionConfig injectionConfig) throws IOException {
        String npmAgentVersion = injectionConfig.getNpmAgentVersion();

        Path cacheDir = InjectionUtil.getDownloadCacheDir(root);
        Path metadataFile = cacheDir.resolve(METADATA_FILENAME);

        ArtifactDigest cachedDigest = ArtifactMetadata.readFromFile(metadataFile)
                .filter(m -> m.isForVersion(npmAgentVersion))
                .map(ArtifactMetadata::digest)
                .orElse(null);

        if (cachedDigest != null) {
            return cachedDigest;
        }

        // Download the NPM agent
        Files.createDirectories(cacheDir);
        Path agentFile = cacheDir.resolve(AGENT_FILENAME);

        URI downloadUrl = createDownloadUrl(npmAgentVersion);
        try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(agentFile))) {
            downloadClient.download(downloadUrl, outputStream);
        }

        // TODO: Consider downloading the checksum file from the repository and verifying the download against it
        ArtifactDigest digest = new ArtifactDigest(Util.getDigestOf(agentFile.toFile()));
        new ArtifactMetadata(npmAgentVersion, digest).writeToFile(metadataFile);

        return digest;
    }

    public ArtifactDigest getDownloadedNpmAgentDigest(Supplier<File> root) throws IOException {
        Path metadataFile = InjectionUtil.getDownloadCacheDir(root).resolve(METADATA_FILENAME);
        return ArtifactMetadata.readFromFile(metadataFile)
                .map(ArtifactMetadata::digest)
                .orElse(null);
    }

    private static URI createDownloadUrl(String npmAgentVersion) {
        return URI.create(
                "%s/@gradle-tech/develocity-agent/-/develocity-agent-%s.tgz".formatted(DEFAULT_REGISTRY_URL, npmAgentVersion)
        );
    }
}
