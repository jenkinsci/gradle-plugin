package hudson.plugins.gradle.injection;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public record ArtifactMetadata(String version, ArtifactDigest digest) {

    private static final String SEPARATOR = ",";

    // Adapter from the old interface
    public ArtifactMetadata(String version, String digest) {
        this(version, new ArtifactDigest(digest));
    }

    public boolean isForVersion(@Nullable String requiredVersion) {
        return version.equals(requiredVersion);
    }

    public void writeToFile(Path metadataFile) throws IOException {
        String content = version + SEPARATOR + digest.digest();
        Files.writeString(metadataFile, content);
    }

    public static Optional<ArtifactMetadata> readFromFile(Path metadataFile) throws IOException {
        if (Files.exists(metadataFile)) {
            String[] metadata = Files.readString(metadataFile).split(SEPARATOR);
            if (metadata.length == 2) {
                String version = metadata[0];
                ArtifactDigest digest = new ArtifactDigest(metadata[1]);
                return Optional.of(new ArtifactMetadata(version, digest));
            }
        }
        return Optional.empty();
    }
}
