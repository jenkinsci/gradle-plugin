package hudson.plugins.gradle;

import hudson.model.TaskListener;

/**
 * @author Gregory Boissinot
 */
public final class GradleLogger extends AbstractLogger {

    public GradleLogger(TaskListener listener) {
        super("Gradle", listener);
    }
}
