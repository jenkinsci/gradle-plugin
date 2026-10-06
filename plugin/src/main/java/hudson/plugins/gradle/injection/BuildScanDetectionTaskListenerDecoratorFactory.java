package hudson.plugins.gradle.injection;

import hudson.Extension;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.plugins.gradle.BuildScanPublishedListener;
import hudson.plugins.gradle.DefaultBuildScanPublishedListener;
import hudson.plugins.gradle.enriched.EnrichedSummaryConfig;
import hudson.plugins.gradle.enriched.ScanDetailService;
import hudson.remoting.Channel;
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
public class BuildScanDetectionTaskListenerDecoratorFactory implements TaskListenerDecorator.Factory {

    private static final Logger LOGGER = Logger.getLogger(BuildScanDetectionTaskListenerDecoratorFactory.class.getName());

    @Override
    @CheckForNull
    public TaskListenerDecorator of(@Nonnull FlowExecutionOwner owner) {
        if (!EnrichedSummaryConfig.get().isGlobalBuildScanDetection()) {
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

    public static class BuildScanDetectionTaskListenerDecorator extends TaskListenerDecorator implements Serializable {
        private static final long serialVersionUID = 2L;

        // Survives serialization to an agent: with DurableTaskStep.USE_WATCHING the agent decorates its own output
        private final BuildScanPublishedListener listener;
        private final String charset;

        public BuildScanDetectionTaskListenerDecorator(Run<?, ?> run) {
            this.listener = new RunBuildScanPublishedListener(run.getExternalizableId());
            this.charset = run.getCharset().name();
        }

        @Nonnull
        @Override
        public OutputStream decorate(@Nonnull OutputStream logger) {
            return new BuildScanDetectionLogProcessor(logger, Charset.forName(charset), listener);
        }
    }

    /**
     * Attaches detected Build Scans to the run. When sent to an agent, it is replaced by a remoting proxy,
     * so Build Scans detected on the agent are still recorded on the controller.
     */
    static final class RunBuildScanPublishedListener implements BuildScanPublishedListener, Serializable {
        private static final long serialVersionUID = 1L;

        private final String runId;

        RunBuildScanPublishedListener(String runId) {
            this.runId = runId;
        }

        @Override
        public void onBuildScanPublished(String scanUrl) {
            Run<?, ?> run = Run.fromExternalizableId(runId);
            if (run == null) {
                LOGGER.log(Level.FINE, "Run {0} not found, ignoring Build Scan {1}", new Object[]{runId, scanUrl});
                return;
            }
            new DefaultBuildScanPublishedListener(run, new ScanDetailService(EnrichedSummaryConfig.get()))
                .onBuildScanPublished(scanUrl);
        }

        private Object writeReplace() {
            Channel channel = Channel.current();
            return channel != null ? channel.export(BuildScanPublishedListener.class, this) : this;
        }
    }
}
