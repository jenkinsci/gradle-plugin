package hudson.plugins.gradle;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Builder;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.tasks.SimpleBuildWrapper;
import org.kohsuke.stapler.DataBoundConstructor;

public class BuildScanBuildWrapper extends SimpleBuildWrapper {

    @DataBoundConstructor
    public BuildScanBuildWrapper() {}

    @Override
    public void setUp(
            Context context,
            Run<?, ?> build,
            FilePath workspace,
            Launcher launcher,
            TaskListener listener,
            EnvVars initialEnvironment) {
        // do nothing
    }

    @CheckForNull
    @Override
    public ConsoleLogFilter createLoggerDecorator(@Nonnull Run<?, ?> build) {
        return new GradleConsoleLogFilter();
    }

    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.wrapper_displayName();
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> project) {
            if (project instanceof FreeStyleProject) {
                for (Builder builder : ((FreeStyleProject) project).getBuildersList()) {
                    if (builder instanceof Gradle) {
                        // disable this wrapper if the current project already uses a Gradle build step
                        return false;
                    }
                }
            }

            return true;
        }
    }
}
