package hudson.plugins.gradle.injection;

import hudson.Extension;
import hudson.model.Queue;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("unused")
@Extension
public class BuildScanDetectionTaskListenerDecoratorFactory implements TaskListenerDecorator.Factory {

    private static final Logger LOGGER = Logger.getLogger(BuildScanDetectionTaskListenerDecoratorFactory.class.getName());

    @Override
    @CheckForNull
    public TaskListenerDecorator of(@Nonnull FlowExecutionOwner owner) {
        if (!InjectionConfig.get().isGlobalBuildScanDetection()) {
            return null;
        }
        try {
            Queue.Executable executable = owner.getExecutable();
            if (executable instanceof WorkflowRun) {
                return new BuildScanDetectionTaskListenerDecorator((WorkflowRun) executable);
            }
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, null, ex);
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    public static class BuildScanDetectionTaskListenerDecorator extends TaskListenerDecorator implements Serializable {
        private static final long serialVersionUID = 1L;

        private final transient Run run;

        public BuildScanDetectionTaskListenerDecorator(Run run) {
            this.run = run;
        }

        @Nonnull
        @Override
        public OutputStream decorate(@Nonnull OutputStream logger) {
            if (run != null) {
                return new BuildScanDetectionLogProcessor(logger, run);
            }
            return logger;
        }
    }
}
