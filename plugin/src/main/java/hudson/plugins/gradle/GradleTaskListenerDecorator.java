package hudson.plugins.gradle;

import hudson.plugins.gradle.enriched.EnrichedSummaryConfig;
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
        // Skip build scan detection in withGradle when global detection handles it
        BuildScanPublishedListener listener = EnrichedSummaryConfig.get().isGlobalBuildScanDetection()
            ? null
            : buildScanListener;
        return new GradleConsoleAnnotator(
            logger,
            StandardCharsets.UTF_8,
            true,
            listener
        );
    }

    @Override
    public List<String> getBuildScans() {
        return buildScanListener.getBuildScans();
    }
}
