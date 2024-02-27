package hudson.plugins.gradle.injection;

public enum MavenExtension {
        GRADLE_ENTERPRISE("gradle-enterprise-maven-extension", ExtensionsVersions.GE_EXTENSION_VERSION, new MavenCoordinates("com.gradle", "gradle-enterprise-maven-extension")),
        CCUD("common-custom-user-data-maven-extension", ExtensionsVersions.CCUD_EXTENSION_VERSION, new MavenCoordinates("com.gradle", "common-custom-user-data-maven-extension")),
        CONFIGURATION("configuration-maven-extension", "1.0.0", new MavenCoordinates("com.gradle", "configuration-maven-extension"));

        private static final String JAR_EXTENSION = ".jar";

        private final String name;
        private final String version;

        private final MavenCoordinates coordinates;

        MavenExtension(String name, String version, MavenCoordinates coordinates) {
            this.name = name;
            this.version = version;
            this.coordinates = coordinates;
        }

        public String getTargetJarName() {
            return name + JAR_EXTENSION;
        }

        public String getEmbeddedJarName() {
            return name + "-" + version + JAR_EXTENSION;
        }

        public MavenCoordinates getCoordinates() {
            return coordinates;
        }
    }
