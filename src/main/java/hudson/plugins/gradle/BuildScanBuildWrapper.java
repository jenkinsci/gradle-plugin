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
import jenkins.tasks.SimpleBuildWrapper;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;

public class BuildScanBuildWrapper extends SimpleBuildWrapper {

    @DataBoundConstructor
    public BuildScanBuildWrapper() {
    }

    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        // do nothing
    }

    @CheckForNull
    @Override
    public ConsoleLogFilter createLoggerDecorator(@Nonnull Run<?, ?> build) {
        return new GradleConsoleLogFilter();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {

        public DescriptorImpl() {
            super(BuildScanBuildWrapper.class);
            load();
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Inspect build log for published Gradle build scans";
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
