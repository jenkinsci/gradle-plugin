package hudson.plugins.gradle.injection.npm;

import hudson.Util;
import hudson.plugins.gradle.injection.ArtifactDigest;
import hudson.plugins.gradle.injection.ArtifactMetadata;
import hudson.plugins.gradle.injection.InjectionConfig;
import hudson.plugins.gradle.injection.download.AgentDownloadClient;
import hudson.plugins.gradle.injection.download.RequestAuthenticator;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import static hudson.plugins.gradle.injection.InjectionUtil.DOWNLOAD_CACHE_DIR;

public class NpmAgentDownloadHandler {

    private static final String FILENAME = "develocity-npm-agent";
    private static final String METADATA_FILENAME = FILENAME + ".meta";
    private static final String AGENT_FILENAME = FILENAME + ".tgz";

    private final AgentDownloadClient downloadClient = new AgentDownloadClient();

    public ArtifactDigest downloadNpmAgent(Supplier<File> root, InjectionConfig injectionConfig) throws IOException {
        String npmAgentVersion = injectionConfig.getNpmAgentVersion();

        Path cacheDir = root.get().toPath().resolve(DOWNLOAD_CACHE_DIR);
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

        URI downloadUrl = URI.create("https://registry.npmjs.org/@gradle-tech/develocity-agent/-/develocity-agent-%s.tgz".formatted(npmAgentVersion));
        try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(agentFile))) {
            downloadClient.download(downloadUrl, RequestAuthenticator.NONE, outputStream);
        }

        ArtifactDigest digest = new ArtifactDigest(Util.getDigestOf(agentFile.toFile()));
        ArtifactMetadata metadata = new ArtifactMetadata(npmAgentVersion, digest);
        metadata.writeToFile(metadataFile);

        return digest;
    }
}
