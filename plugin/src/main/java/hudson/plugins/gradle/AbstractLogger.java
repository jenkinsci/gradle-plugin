package hudson.plugins.gradle;

import hudson.model.TaskListener;
import java.io.Serializable;

abstract class AbstractLogger implements Serializable {

    private final String prefix;
    private final TaskListener listener;

    AbstractLogger(String prefix, TaskListener listener) {
        this.prefix = prefix;
        this.listener = listener;
    }

    public final void info(String message) {
        listener.getLogger().printf("[%s] - %s%n", prefix, message);
    }

    public final void error(String message) {
        listener.getLogger().printf("[%s] - [ERROR] - %s%n", prefix, message);
    }
}
