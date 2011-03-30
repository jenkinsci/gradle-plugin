package hudson.plugins.gradle;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.EnvironmentSpecific;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;


public class GradleInstallation extends ToolInstallation
        implements EnvironmentSpecific<GradleInstallation>, NodeSpecific<GradleInstallation> {

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


    public String getExecutable(Launcher launcher) throws IOException, InterruptedException {
        return launcher.getChannel().call(new Callable<String, IOException>() {
            public String call() throws IOException {
                File exe = getExeFile();
                if (exe.exists()) {
                    return exe.getPath();
                }
                return null;
            }
        });
    }

    public String getWrapperExecutable(Launcher launcher, final AbstractBuild<?, ?> build)
            throws IOException, InterruptedException {
        return launcher.getChannel().call(new Callable<String, IOException>() {
            public String call() throws IOException {
                File exe = getWrapperExeFile(build);
                if (exe.exists()) {
                    return exe.getPath();
                }
                return null;
            }
        });
    }

    private File getExeFile() {
        String execName;
        if (Functions.isWindows()) {
            execName = "gradle.bat";
        } else {
            execName = "gradle";
        }
        String antHome = Util.replaceMacro(gradleHome, EnvVars.masterEnvVars);
        return new File(antHome, "bin/" + execName);
    }

    private File getWrapperExeFile(AbstractBuild<?, ?> build) {
        String execName;
        if (Functions.isWindows()) {
            execName = "gradlew.bat";
        } else {
            execName = "gradlew";
        }
        return new File(build.getModuleRoot().getRemote(), execName);
    }

    private static final long serialVersionUID = 1L;

    public GradleInstallation forEnvironment(EnvVars environment) {
        return new GradleInstallation(getName(), environment.expand(gradleHome), getProperties().toList());
    }

    public GradleInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new GradleInstallation(getName(), translateFor(node, log), getProperties().toList());
    }

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<GradleInstallation> {

        public DescriptorImpl() {
        }

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
            return Hudson.getInstance().getDescriptorByType(Gradle.DescriptorImpl.class).getInstallations();
        }

        @Override
        public void setInstallations(GradleInstallation... installations) {
            Hudson.getInstance().getDescriptorByType(Gradle.DescriptorImpl.class).setInstallations(installations);
        }


    }

}
