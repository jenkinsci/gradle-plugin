package hudson.plugins.gradle.injection;

public record ArtifactDigest(String digest) {

    public boolean matches(String otherDigest) {
        return digest.equals(otherDigest);
    }
}
