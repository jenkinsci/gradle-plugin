package hudson.plugins.gradle;

import hudson.model.TaskListener;

public final class GradleEnterpriseLogger extends AbstractLogger {

    public GradleEnterpriseLogger(TaskListener listener) {
        super("Gradle Enterprise", listener);
    }
}
