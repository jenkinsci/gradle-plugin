package hudson.plugins.gradle;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

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
import jenkins.security.MasterToSlaveCallable;


/**
 * Gradle Installation in Jenkins.
 * 
 * @author Gregory Boissinot
 */
public class GradleInstallation 
        extends ToolInstallation
        implements EnvironmentSpecific<GradleInstallation>, NodeSpecific<GradleInstallation>, Serializable {

    private static final long serialVersionUID = -1875114350592080381L;
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
        if (home.endsWith("/") || home.endsWith("\\")) {
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

    public String getExecutable(final Launcher launcher) throws IOException, InterruptedException {
        final File exe = launcher.getChannel().call(new MasterToSlaveCallable<File, IOException>() {

            private static final long serialVersionUID = 5572701806697595613L;

            @Override
            public File call() throws IOException {
                return getExeFile();
            }
        });

        final boolean existis = launcher.getChannel().call(new MasterToSlaveCallable<Boolean, IOException>() {

            private static final long serialVersionUID = 5572701806697595613L;

            @Override
            public Boolean call() throws IOException {
                return exe.exists();
            }
        });
        if (!existis) {
            launcher.getListener().error("Gradle executable does not exist: " + exe.getPath());
            return null;
        } else {
            return exe.getPath();
        }
    }

    private File getExeFile() {
        String execName = (Functions.isWindows()) ? WINDOWS_GRADLE_COMMAND : UNIX_GRADLE_COMMAND;
        String gradleHomeDir = Util.replaceMacro(gradleHome, EnvVars.masterEnvVars);
        return new File(gradleHomeDir, "bin/" + execName);
    }

    @Override
    public GradleInstallation forEnvironment(EnvVars environment) {
        return new GradleInstallation(getName(), environment.expand(gradleHome), getProperties().toList());
    }

    @Override
    public GradleInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new GradleInstallation(getName(), translateFor(node, log), getProperties().toList());
    }

    @Extension @Symbol("gradle")
    public static class DescriptorImpl extends ToolDescriptor<GradleInstallation> {

        public DescriptorImpl() {
        }

        @Inject
        private Gradle.DescriptorImpl gradleDescriptor;

        @Override
        public String getDisplayName() {
            return Messages.installer_displayName();
        }

        @Override
        public List<? extends ToolInstaller> getDefaultInstallers() {
            return Collections.singletonList(new GradleInstaller(null));
        }

        // for compatibility reasons, the persistence is done by GradleBuilder.DescriptorImpl
        @Override
        public GradleInstallation[] getInstallations() {
            return gradleDescriptor.getInstallations();
        }

        @Override
        public void setInstallations(GradleInstallation... installations) {
            gradleDescriptor.setInstallations(installations);
        }
    }

}
