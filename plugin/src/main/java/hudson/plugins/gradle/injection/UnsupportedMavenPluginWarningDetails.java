package hudson.plugins.gradle.injection;

import hudson.util.VersionNumber;

public final class UnsupportedMavenPluginWarningDetails {

    private final VersionNumber mavenPluginVersion;

    public UnsupportedMavenPluginWarningDetails(VersionNumber mavenPluginVersion) {
        this.mavenPluginVersion = mavenPluginVersion;
    }

    public VersionNumber getMavenPluginVersion() {
        return mavenPluginVersion;
    }

    public VersionNumber getMinimumSupportedVersion() {
        return InjectionUtil.MINIMUM_SUPPORTED_MAVEN_PLUGIN_VERSION;
    }
}
