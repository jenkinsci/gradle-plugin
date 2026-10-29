package hudson.plugins.gradle;

import hudson.remoting.Channel;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;

import javax.annotation.Nonnull;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class GradleTaskListenerDecorator extends TaskListenerDecorator implements BuildScansAware {

    private final SimpleBuildScanPublishedListener buildScanListener;

    public GradleTaskListenerDecorator() {
        buildScanListener = new SimpleBuildScanPublishedListener();
    }

    @Nonnull
    @Override
    public OutputStream decorate(@Nonnull OutputStream logger) {
        return annotate(logger, buildScanListener);
    }

    @Override
    public List<String> getBuildScans() {
        return buildScanListener.getBuildScans();
    }

    private Object writeReplace() {
        Channel channel = Channel.current();
        return channel != null
            ? new AgentTaskListenerDecorator(channel.export(BuildScanPublishedListener.class, buildScanListener))
            : this;
    }

    private static OutputStream annotate(OutputStream logger, BuildScanPublishedListener buildScanListener) {
        return new GradleConsoleAnnotator(
            logger,
            StandardCharsets.UTF_8,
            true,
            buildScanListener
        );
    }

    private static final class AgentTaskListenerDecorator extends TaskListenerDecorator {
        private static final long serialVersionUID = 1L;

        private final BuildScanPublishedListener buildScanListener;

        AgentTaskListenerDecorator(BuildScanPublishedListener buildScanListener) {
            this.buildScanListener = buildScanListener;
        }

        @Nonnull
        @Override
        public OutputStream decorate(@Nonnull OutputStream logger) {
            return annotate(logger, buildScanListener);
        }
    }
}
