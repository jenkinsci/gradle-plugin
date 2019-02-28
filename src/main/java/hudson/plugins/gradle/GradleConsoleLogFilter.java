package hudson.plugins.gradle;

import hudson.Extension;
import hudson.console.ConsoleLogFilter;
import hudson.model.FreeStyleBuild;
import hudson.model.Run;
import hudson.tasks.Builder;

import java.io.IOException;
import java.io.OutputStream;

@Extension
public class GradleConsoleLogFilter extends ConsoleLogFilter implements BuildScanPublishedListener {
    private Run build;

    @Override
    public OutputStream decorateLogger(Run build, OutputStream logger) throws IOException, InterruptedException {
        this.build = build;

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
        gradleConsoleAnnotator.addBuildScanPublishedListener(this);

        return gradleConsoleAnnotator;
    }

    @Override
    public void onBuildScanPublished(String scanUrl) {
        BuildScanAction action = build.getAction(BuildScanAction.class);

        if (action == null) {
            action = new BuildScanAction();
            action.addScanUrl(scanUrl);

            build.addAction(action);
        } else {
            action.addScanUrl(scanUrl);
        }
    }
}
