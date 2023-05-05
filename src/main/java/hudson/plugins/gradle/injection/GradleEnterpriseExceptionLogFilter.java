package hudson.plugins.gradle.injection;

import hudson.Extension;
import hudson.console.ConsoleLogFilter;
import hudson.model.Run;

import java.io.OutputStream;
import java.io.Serializable;

@Extension
public class GradleEnterpriseExceptionLogFilter extends ConsoleLogFilter implements Serializable {

    @Override
    public OutputStream decorateLogger(Run build, OutputStream logger) {
        if (InjectionConfig.get().isCheckForBuildAgentErrors()) {
            return new GradleEnterpriseExceptionLogProcessor(logger, build);
        }
        return logger;
    }
}
