package hudson.plugins.gradle.injection;

import hudson.plugins.gradle.BuildToolType;

public interface GradleEnterpriseExceptionDetector {

    BuildToolType getBuildToolType();

    boolean detect(String line);
}
