package hudson.plugins.gradle.injection;

import java.io.Serializable;
import java.util.Objects;

/**
 * Describes a set of Maven coordinates, represented as a GAV.
 */
public final class MavenCoordinates implements Serializable {

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MavenCoordinates that = (MavenCoordinates) o;
        return Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version);
    }

    static MavenCoordinates parseCoordinates(String groupAndArtifact) {
        if (groupAndArtifact == null || groupAndArtifact.trim().isEmpty()) {
            return null;
        } else {
            String[] ga = groupAndArtifact.split(":");
            if (ga.length == 2) {
                return new MavenCoordinates(ga[0], ga[1]);
            } else if (ga.length == 3) {
                return new MavenCoordinates(ga[0], ga[1], ga[2]);
            } else {
                return null;
            }
        }
    }

    static boolean isValid(String value) {
        return MavenCoordinates.parseCoordinates(value) != null;
    }

    String groupId() {
        return groupId;
    }

    String artifactId() {
        return artifactId;
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%s", groupId, artifactId, version);
    }

}
