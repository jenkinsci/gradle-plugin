package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Logger;

public class GradleBuildScanInjection implements BuildScanInjection {

    private static final Logger LOGGER = Logger.getLogger(GradleBuildScanInjection.class.getName());

    private static final String JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_HOME = "JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_HOME";

    private static final String RESOURCE_INIT_SCRIPT_GRADLE = "/hudson/plugins/gradle/injection/init-script.gradle";
    private static final String INIT_DIR = "init.d";
    private static final String GRADLE_DIR = ".gradle";
    private static final String GRADLE_INIT_FILE = "init-build-scan.gradle";

    @Override
    public String getActivationEnvironmentVariableName() {
        return "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION";
    }

    @Override
    public void inject(Node node, EnvVars envGlobal, EnvVars envComputer) {
        try {
            String initScriptDirectory = getInitScriptDirectory(envGlobal, envComputer);

            if (isEnabled(envGlobal)) {
                copyInitScript(node.getChannel(), initScriptDirectory);
            } else {
                removeInitScript(node.getChannel(), initScriptDirectory);
            }
        } catch (IllegalStateException e) {
            LOGGER.warning("Error: " + e.getMessage());
        }
    }

    private String getInitScriptDirectory(EnvVars envGlobal, EnvVars envComputer) {
        String homeOverride = getEnv(envGlobal, JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_HOME);
        if (homeOverride != null) {
            return homeOverride + "/" + GRADLE_DIR + "/" + INIT_DIR;
        } else {
            String home = getEnv(envComputer, "HOME");
            if(home == null){
                throw new IllegalStateException("HOME is not set");
            }
            return home + "/" + GRADLE_DIR + "/" + INIT_DIR;
        }
    }

    private void copyInitScript(VirtualChannel channel, String initScriptDirectory) {
        try {
            FilePath gradleInitScriptFile = getInitScriptFile(channel, initScriptDirectory);
            if (!gradleInitScriptFile.exists()) {
                FilePath gradleInitScriptDirectory = new FilePath(channel, initScriptDirectory);
                if (!gradleInitScriptDirectory.exists()) {
                    LOGGER.fine("create init script directory");
                    gradleInitScriptDirectory.mkdirs();
                }

                LOGGER.fine("copy init script file");
                gradleInitScriptFile.copyFrom(
                        Objects.requireNonNull(BuildScanInjectionListener.class.getResourceAsStream(RESOURCE_INIT_SCRIPT_GRADLE))
                );
            }
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private void removeInitScript(VirtualChannel channel, String initScriptDirectory) {
        try {
            FilePath gradleInitScriptFile = getInitScriptFile(channel, initScriptDirectory);
            if (gradleInitScriptFile.exists()) {
                LOGGER.fine("delete init script file");
                if (!gradleInitScriptFile.delete()) {
                    throw new IllegalStateException("Error while deleting init script");
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private FilePath getInitScriptFile(VirtualChannel channel, String initScriptDirectory) {
        if (initScriptDirectory == null) {
            throw new IllegalStateException("init script directory is null");
        }
        return new FilePath(channel, initScriptDirectory + "/" + GRADLE_INIT_FILE);
    }

}
