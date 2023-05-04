package hudson.plugins.gradle;

import hudson.console.ConsoleLogFilter;
import hudson.model.FreeStyleBuild;
import hudson.model.Run;
import hudson.plugins.gradle.enriched.EnrichedSummaryConfig;
import hudson.plugins.gradle.enriched.ScanDetailService;
import hudson.tasks.Builder;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

public class GradleConsoleLogFilter extends ConsoleLogFilter implements Serializable {

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

        ScanDetailService scanDetailService = new ScanDetailService(EnrichedSummaryConfig.get());
        DefaultBuildScanPublishedListener buildScanListener = new DefaultBuildScanPublishedListener(build, scanDetailService);

        return new GradleConsoleAnnotator(logger, build.getCharset(), usesGradleBuilder, buildScanListener);
    }
}
