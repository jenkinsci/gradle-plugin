package hudson.plugins.gradle;

import hudson.console.ConsoleLogFilter;
import hudson.model.FreeStyleBuild;
import hudson.model.Run;
import hudson.tasks.Builder;

import java.io.IOException;
import java.io.OutputStream;

public class GradleConsoleLogFilter extends ConsoleLogFilter {

    @Override
    public OutputStream decorateLogger(Run build, OutputStream logger) throws IOException, InterruptedException {
        boolean usesGradleBuilder = false;
        if (build instanceof FreeStyleBuild) {
            for (Builder builder : ((FreeStyleBuild) build).getProject().getBuildersList()) {
                if (builder instanceof Gradle) {
                    usesGradleBuilder = true;
                    break;
                }
            }
        }

        GradleConsoleAnnotator gradleConsoleAnnotator = new GradleConsoleAnnotator(logger, build.getCharset(), usesGradleBuilder);
        gradleConsoleAnnotator.addBuildScanPublishedListener(new DefaultBuildScanPublishedListener(build));

        return gradleConsoleAnnotator;
    }
}
