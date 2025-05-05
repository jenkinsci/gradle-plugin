package hudson.plugins.gradle;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Gregory Boissinot
 */
public class GradleInstallation extends ToolInstallation
        implements EnvironmentSpecific<GradleInstallation>, NodeSpecific<GradleInstallation>, Serializable {

    public static final String UNIX_GRADLE_COMMAND = "gradle";
    public static final String WINDOWS_GRADLE_COMMAND = "gradle.bat";
    public static final String UNIX_GRADLE_WRAPPER_COMMAND = "gradlew";
    public static final String WINDOWS_GRADLE_WRAPPER_COMMAND = "gradlew.bat";

    private final String gradleHome;

    @DataBoundConstructor
    public GradleInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(name, launderHome(home), properties);
        this.gradleHome = super.getHome();
    }

    private static String launderHome(String home) {
        if (home != null && (home.endsWith("/") || home.endsWith("\\"))) {
            // see https://issues.apache.org/bugzilla/show_bug.cgi?id=26947
            // Ant doesn't like the trailing slash, especially on Windows
            return home.substring(0, home.length() - 1);
        } else {
            return home;
        }
    }

    @Override
    public String getHome() {
        if (gradleHome != null) {
            return gradleHome;
        }
        return super.getHome();
    }

    @Override
    public void buildEnvVars(EnvVars env) {
        env.put("PATH+GRADLE", getHome() + "/bin");
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public String getExecutable(Launcher launcher) throws IOException, InterruptedException {
        return launcher.getChannel().call(new GetExeFile(gradleHome));
    }

    private static final class GetExeFile extends MasterToSlaveCallable<String, IOException> {
        private final String gradleHome;

        GetExeFile(String gradleHome) {
            this.gradleHome = gradleHome;
        }

        @Override
        public String call() throws IOException {
            String execName = (Functions.isWindows()) ? WINDOWS_GRADLE_COMMAND : UNIX_GRADLE_COMMAND;
            String gradleHomeSubstituted = Util.replaceMacro(gradleHome, EnvVars.masterEnvVars);
            File exe = new File(gradleHomeSubstituted, "bin/" + execName);
            if (exe.exists()) {
                return exe.getPath();
            }
            return null;
        }
    }

    public GradleInstallation forEnvironment(EnvVars environment) {
        return new GradleInstallation(
                getName(), environment.expand(gradleHome), getProperties().toList());
    }

    public GradleInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new GradleInstallation(
                getName(), translateFor(node, log), getProperties().toList());
    }

    @Extension
    @Symbol("gradle")
    public static class DescriptorImpl extends ToolDescriptor<GradleInstallation> {

        public DescriptorImpl() {}

        @Override
        public String getDisplayName() {
            return Messages.installer_displayName();
        }

        @Override
        public List<? extends ToolInstaller> getDefaultInstallers() {
            return Collections.singletonList(new GradleInstaller(null));
        }

        // for compatibility reasons, the persistence is done by Gradle.DescriptorImpl

        @Override
        public GradleInstallation[] getInstallations() {
            return getGradleDescriptor().getInstallations();
        }

        @Override
        public void setInstallations(GradleInstallation... installations) {
            getGradleDescriptor().setInstallations(installations);
        }

        private static Gradle.DescriptorImpl getGradleDescriptor() {
            return Jenkins.getActiveInstance().getDescriptorByType(Gradle.DescriptorImpl.class);
        }
    }
}
