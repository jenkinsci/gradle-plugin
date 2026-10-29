package hudson.plugins.gradle.injection;

import hudson.Extension;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.plugins.gradle.BuildAgentError;
import hudson.remoting.Channel;
import hudson.security.ACL;
import hudson.security.ACLContext;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
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

    public static class DevelocityExceptionTaskListenerDecorator extends TaskListenerDecorator implements Serializable {
        private static final long serialVersionUID = 2L;

        // Survives serialization to an agent: with DurableTaskStep.USE_WATCHING the agent decorates its own output
        private final BuildAgentErrorListener listener;
        private final String charset;

        public DevelocityExceptionTaskListenerDecorator(Run<?, ?> run) {
            this.listener = new RunBuildAgentErrorListener(run.getExternalizableId());
            this.charset = run.getCharset().name();
        }

        @Nonnull
        @Override
        public OutputStream decorate(@Nonnull OutputStream logger) {
            return new DevelocityExceptionLogProcessor(logger, Charset.forName(charset), listener);
        }
    }

    /**
     * Attaches detected build agent errors to the run. When sent to an agent, it is replaced by a remoting proxy,
     * so build agent errors detected on the agent are still recorded on the controller.
     */
    static final class RunBuildAgentErrorListener implements BuildAgentErrorListener, Serializable {
        private static final long serialVersionUID = 1L;

        private final String runId;

        RunBuildAgentErrorListener(String runId) {
            this.runId = runId;
        }

        @Override
        public void onBuildAgentError(BuildAgentError buildAgentError) {
            Run<?, ?> run;
            // Log processing for steps on the built-in node runs as anonymous, which may not see the job
            try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
                run = Run.fromExternalizableId(runId);
            }
            if (run == null) {
                LOGGER.log(Level.FINE, "Run {0} not found, ignoring build agent error", runId);
                return;
            }
            new DefaultBuildAgentErrorListener(run).onBuildAgentError(buildAgentError);
        }

        private Object writeReplace() {
            Channel channel = Channel.current();
            return channel != null ? channel.export(BuildAgentErrorListener.class, this) : this;
        }
    }

    static boolean isBuildAgentErrorsEnabled() {
        InjectionConfig injectionConfig = InjectionConfig.get();
        return injectionConfig.isEnabled() && injectionConfig.isCheckForBuildAgentErrors();
    }

}
