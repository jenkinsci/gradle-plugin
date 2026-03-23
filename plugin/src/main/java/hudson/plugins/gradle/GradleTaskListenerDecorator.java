package hudson.plugins.gradle;

import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;

import javax.annotation.Nonnull;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

public class GradleTaskListenerDecorator extends TaskListenerDecorator implements BuildScansAware {

    private final SimpleBuildScanPublishedListener buildScanListener;

    public GradleTaskListenerDecorator() {
        buildScanListener = new SimpleBuildScanPublishedListener();
    }

    @Nonnull
    @Override
    public OutputStream decorate(@Nonnull OutputStream logger) {
        return new GradleConsoleAnnotator(
            logger,
            StandardCharsets.UTF_8,
            true,
            buildScanListener
        );
    }

    @Override
    public List<String> getBuildScans() {
        return buildScanListener.getBuildScans();
    }
}
