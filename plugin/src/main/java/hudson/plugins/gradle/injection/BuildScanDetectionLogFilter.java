package hudson.plugins.gradle.injection;

import hudson.Extension;
import hudson.console.ConsoleLogFilter;
import hudson.model.Run;
import hudson.plugins.gradle.enriched.EnrichedSummaryConfig;

import java.io.OutputStream;
import java.io.Serializable;

@SuppressWarnings("unused")
@Extension
public class BuildScanDetectionLogFilter extends ConsoleLogFilter implements Serializable {

    @Override
    public OutputStream decorateLogger(Run build, OutputStream logger) {
        if (EnrichedSummaryConfig.get().isGlobalBuildScanDetection() && build != null) {
            return new BuildScanDetectionLogProcessor(logger, build);
        }
        return logger;
    }
}
