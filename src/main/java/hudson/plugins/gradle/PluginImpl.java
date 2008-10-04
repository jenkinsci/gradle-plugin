package hudson.plugins.gradle;

import hudson.Plugin;
import hudson.tasks.BuildStep;

/**
 * Gradle plugin entry point.
 * 
 * @author Gregory Boissinot - Zenika
 * @plugin
 */
public class PluginImpl extends Plugin {
    public void start() throws Exception {
        BuildStep.BUILDERS.add(Gradle.DESCRIPTOR);
    }
}
