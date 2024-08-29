package hudson.plugins.gradle.injection;

public enum MavenExtension {
        DEVELOCITY("develocity-maven-extension", new MavenCoordinates("com.gradle", "develocity-maven-extension")),
        GRADLE_ENTERPRISE("gradle-enterprise-maven-extension", new MavenCoordinates("com.gradle", "gradle-enterprise-maven-extension")),
        CCUD("common-custom-user-data-maven-extension", new MavenCoordinates("com.gradle", "common-custom-user-data-maven-extension")),
        CONFIGURATION("configuration-maven-extension", new MavenCoordinates("com.gradle", "configuration-maven-extension"));

        private static final String JAR_EXTENSION = ".jar";

        private final MavenCoordinates coordinates;

        private final String name;

        MavenExtension(String name, MavenCoordinates coordinates) {
            this.name = name;
            this.coordinates = coordinates;
        }

        public String getTargetJarName() {
            return name + JAR_EXTENSION;
        }

        // Only used for CONFIGURATION extension as the embedded JAR contains the version
        public String getEmbeddedJarName() {
            return name + "-1.0.0" + JAR_EXTENSION;
        }

        public MavenCoordinates getCoordinates() {
            return coordinates;
        }

        public String getName() {
            return name;
        }
}
