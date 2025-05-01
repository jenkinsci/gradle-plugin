package hudson.plugins.gradle;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;

public class GradleTaskListenerDecorator extends TaskListenerDecorator implements BuildScansAware {

    private final SimpleBuildScanPublishedListener buildScanListener;

    public GradleTaskListenerDecorator() {
        buildScanListener = new SimpleBuildScanPublishedListener();
    }

    @Nonnull
    @Override
    public OutputStream decorate(@Nonnull OutputStream logger) {
        return new GradleConsoleAnnotator(logger, StandardCharsets.UTF_8, true, buildScanListener);
    }

    @Override
    public List<String> getBuildScans() {
        return buildScanListener.getBuildScans();
    }
}
