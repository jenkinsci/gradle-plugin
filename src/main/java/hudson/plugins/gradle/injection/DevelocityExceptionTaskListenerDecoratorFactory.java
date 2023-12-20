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
public class DevelocityExceptionTaskListenerDecoratorFactory implements TaskListenerDecorator.Factory {

    private static final Logger LOGGER = Logger.getLogger(DevelocityExceptionTaskListenerDecoratorFactory.class.getName());

    @Override
    @CheckForNull
    public TaskListenerDecorator of(@Nonnull FlowExecutionOwner owner) {
        if (!isBuildAgentErrorsEnabled()) {
            return null;
        }
        try {
            Queue.Executable executable = owner.getExecutable();
            if (executable instanceof WorkflowRun) {
                return new DevelocityExceptionTaskListenerDecorator((WorkflowRun) executable);
            }
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, null, ex);
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    public static class DevelocityExceptionTaskListenerDecorator extends TaskListenerDecorator implements Serializable {
        private static final long serialVersionUID = 1L;

        private final transient Run run;

        public DevelocityExceptionTaskListenerDecorator(Run run) {
            this.run = run;
        }

        @Nonnull
        @Override
        public OutputStream decorate(@Nonnull OutputStream logger) {
            if (run != null) {
                return new DevelocityExceptionLogProcessor(logger, run);
            }
            return logger;
        }
    }

    static boolean isBuildAgentErrorsEnabled() {
        InjectionConfig injectionConfig = InjectionConfig.get();
        return injectionConfig.isEnabled() && injectionConfig.isCheckForBuildAgentErrors();
    }

}
