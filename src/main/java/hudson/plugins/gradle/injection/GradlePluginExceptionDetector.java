package hudson.plugins.gradle.injection;

import hudson.plugins.gradle.BuildToolType;

public final class GradlePluginExceptionDetector implements GradleEnterpriseExceptionDetector {

    @Override
    public BuildToolType getBuildToolType() {
        return BuildToolType.GRADLE;
    }

    @Override
    public boolean detect(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }
        return line.startsWith("Internal error in Gradle Enterprise Gradle plugin:");
    }
}
