package hudson.plugins.gradle.injection;

import hudson.Extension;
import hudson.console.ConsoleLogFilter;
import hudson.model.Run;

import java.io.OutputStream;
import java.io.Serializable;

@SuppressWarnings("unused")
@Extension
public class BuildScanDetectionLogFilter extends ConsoleLogFilter implements Serializable {

    @Override
    public OutputStream decorateLogger(Run build, OutputStream logger) {
        InjectionConfig injectionConfig = InjectionConfig.get();
        if (injectionConfig.isGlobalBuildScanDetection() && build != null) {
            return new BuildScanDetectionLogProcessor(logger, build);
        }
        return logger;
    }
}
