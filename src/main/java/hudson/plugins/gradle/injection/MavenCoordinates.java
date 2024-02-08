package hudson.plugins.gradle.injection;

/**
 * Describes a set of Maven coordinates, represented as a GAV.
 */
public final class MavenCoordinates {

    private final String groupId;
    private final String artifactId;
    private final String version;

    public MavenCoordinates(String groupId, String artifactId) {
        this(groupId, artifactId, "unspecified");
    }

    public MavenCoordinates(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    String groupId() {
        return groupId;
    }

    String artifactId() {
        return artifactId;
    }

    String version() {
        return version;
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%s", groupId, artifactId, version);
    }

}
