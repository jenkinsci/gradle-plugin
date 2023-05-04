package hudson.plugins.gradle;

import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GradleTaskListenerDecorator extends TaskListenerDecorator {

    private final List<String> buildScans = new ArrayList<>();

    @Nonnull
    @Override
    public OutputStream decorate(@Nonnull OutputStream logger) throws IOException, InterruptedException {
        return new GradleConsoleAnnotator(
            logger,
            StandardCharsets.UTF_8,
            true,
            buildScans::add
        );
    }

    public List<String> getBuildScans() {
        return new ArrayList<>(buildScans);
    }
}
