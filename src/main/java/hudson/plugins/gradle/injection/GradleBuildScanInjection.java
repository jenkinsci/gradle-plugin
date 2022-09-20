package hudson.plugins.gradle.injection;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.plugins.gradle.injection.CopyUtil.copyResourceToNode;
import static hudson.plugins.gradle.injection.CopyUtil.unsafeResourceDigest;

public class GradleBuildScanInjection implements BuildScanInjection {

    private static final Logger LOGGER = Logger.getLogger(GradleBuildScanInjection.class.getName());

    private static final String JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME = "JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME";
    private static final String JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_HOME = "JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_HOME";
    private static final String JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION = "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION";
    private static final String JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL = "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL";

    static final String FEATURE_TOGGLE_DISABLED_NODES = "JENKINSGRADLEPLUGIN_GRADLE_INJECTION_DISABLED_NODES";
    static final String FEATURE_TOGGLE_ENABLED_NODES = "JENKINSGRADLEPLUGIN_GRADLE_INJECTION_ENABLED_NODES";

    private static final String RESOURCE_INIT_SCRIPT_GRADLE = "init-script.gradle";
    private static final String INIT_DIR = "init.d";
    private static final String GRADLE_DIR = ".gradle";
    private static final String GRADLE_INIT_FILE = "init-build-scan.gradle";

    private final Supplier<String> initScriptDigest = Suppliers.memoize(() -> unsafeResourceDigest(RESOURCE_INIT_SCRIPT_GRADLE));

    @Override
    public String getActivationEnvironmentVariableName() {
        return JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION;
    }

    @Override
    public void inject(Node node, EnvVars envGlobal, EnvVars envComputer) {
        try {
            String initScriptDirectory = getInitScriptDirectory(envGlobal, envComputer);

            if (injectionEnabledForNode(node, envGlobal)) {
                if (!isGradleEnterpriseUrlSet(envGlobal)) {
                    throw new IllegalStateException(
                        String.format("Required environment variable '%s' is not set",
                            JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL));
                }
                copyInitScript(node.getChannel(), initScriptDirectory);
            } else {
                removeInitScript(node.getChannel(), initScriptDirectory);
            }
        } catch (IllegalStateException e) {
            if (injectionEnabledForNode(node, envGlobal)) {
                LOGGER.log(Level.WARNING, "Unexpected exception while injecting build scans for Gradle", e);
            }
        }
    }

    @Override
    public String getEnabledNodesEnvironmentVariableName() {
        return FEATURE_TOGGLE_ENABLED_NODES;
    }

    @Override
    public String getDisabledNodesEnvironmentVariableName() {
        return FEATURE_TOGGLE_DISABLED_NODES;
    }

    private static String getInitScriptDirectory(EnvVars envGlobal, EnvVars envComputer) {
        String gradleHomeOverride = EnvUtil.getEnv(envGlobal, JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME);
        String homeOverride = EnvUtil.getEnv(envGlobal, JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_HOME);
        if (gradleHomeOverride != null) {
            return gradleHomeOverride + "/" + INIT_DIR;
        } else if (homeOverride != null) {
            return homeOverride + "/" + GRADLE_DIR + "/" + INIT_DIR;
        } else {
            String home = EnvUtil.getEnv(envComputer, "HOME");
            if (home == null) {
                throw new IllegalStateException("HOME is not set");
            }
            return home + "/" + GRADLE_DIR + "/" + INIT_DIR;
        }
    }

    private void copyInitScript(VirtualChannel channel, String initScriptDirectory) {
        try {
            FilePath gradleInitScriptFile = getInitScriptFile(channel, initScriptDirectory);
            if (gradleInitScriptFile.exists()) {
                if (initScriptNotChanged(gradleInitScriptFile)) {
                    LOGGER.fine("init script already exists");
                    return;
                }
                // File has changed, remove the old version and copy the new one
                removeInitScript(channel, initScriptDirectory);
            }

            FilePath gradleInitScriptDirectory = new FilePath(channel, initScriptDirectory);
            if (!gradleInitScriptDirectory.exists()) {
                LOGGER.fine("create init script directory");
                gradleInitScriptDirectory.mkdirs();
            }

            LOGGER.fine("copy init script file");
            copyResourceToNode(gradleInitScriptFile, RESOURCE_INIT_SCRIPT_GRADLE);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean initScriptNotChanged(FilePath gradleInitScriptFile) throws IOException, InterruptedException {
        String existingFileDigest = gradleInitScriptFile.digest();

        return Objects.equals(existingFileDigest, initScriptDigest.get());
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

    private static FilePath getInitScriptFile(VirtualChannel channel, String initScriptDirectory) {
        if (initScriptDirectory == null) {
            throw new IllegalStateException("init script directory is null");
        }
        return new FilePath(channel, initScriptDirectory + "/" + GRADLE_INIT_FILE);
    }

    private static boolean isGradleEnterpriseUrlSet(EnvVars envGlobal) {
        return EnvUtil.isSet(envGlobal, JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL);
    }
}
