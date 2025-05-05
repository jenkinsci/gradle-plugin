package hudson.plugins.gradle;

import hudson.model.TaskListener;

public final class DevelocityLogger extends AbstractLogger {

    public DevelocityLogger(TaskListener listener) {
        super("Develocity", listener);
    }
}
