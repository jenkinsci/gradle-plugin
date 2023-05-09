package hudson.plugins.gradle.injection;

import hudson.console.ConsoleNote;
import hudson.plugins.gradle.BuildToolType;

public final class MavenExtensionExceptionDetector implements GradleEnterpriseExceptionDetector {

    @Override
    public BuildToolType getBuildToolType() {
        return BuildToolType.MAVEN;
    }

    @Override
    public boolean detect(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }
        String withoutNotes = ConsoleNote.removeNotes(line);
        if (withoutNotes.isEmpty()) {
            return false;
        }
        return withoutNotes.startsWith("[ERROR] Internal error in Gradle Enterprise Maven extension:");
    }
}
