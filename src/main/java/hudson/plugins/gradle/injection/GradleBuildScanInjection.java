package hudson.plugins.gradle.injection;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Node;
import hudson.plugins.gradle.util.CollectionUtil;
import hudson.remoting.VirtualChannel;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static hudson.plugins.gradle.injection.CopyUtil.copyResourceToNode;
import static hudson.plugins.gradle.injection.CopyUtil.unsafeResourceDigest;

public class GradleBuildScanInjection implements BuildScanInjection {

    private static final Logger LOGGER = Logger.getLogger(GradleBuildScanInjection.class.getName());

    private static final String JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME = "JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME";
    private static final String JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_HOME = "JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_HOME";

    private static final String JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL = "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL";
    private static final String JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER = "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER";
    private static final String JENKINSGRADLEPLUGIN_GRADLE_PLUGIN_REPOSITORY_URL = "JENKINSGRADLEPLUGIN_GRADLE_PLUGIN_REPOSITORY_URL";
    private static final String JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION = "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION";
    private static final String JENKINSGRADLEPLUGIN_CCUD_PLUGIN_VERSION = "JENKINSGRADLEPLUGIN_CCUD_PLUGIN_VERSION";

    private static final List<String> ALL_INJECTED_ENVIRONMENT_VARIABLES =
        Arrays.asList(
            JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL,
            JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER,
            JENKINSGRADLEPLUGIN_GRADLE_PLUGIN_REPOSITORY_URL,
            JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION,
            JENKINSGRADLEPLUGIN_CCUD_PLUGIN_VERSION
        );

    private static final String HOME = "HOME";

    private static final String RESOURCE_INIT_SCRIPT_GRADLE = "init-script.gradle";
    private static final String INIT_DIR = "init.d";
    private static final String GRADLE_DIR = ".gradle";
    private static final String GRADLE_INIT_FILE = "init-build-scan.gradle";

    private final Supplier<String> initScriptDigest = Suppliers.memoize(() -> unsafeResourceDigest(RESOURCE_INIT_SCRIPT_GRADLE));

    @Override
    public boolean isEnabled(Node node) {
        InjectionConfig config = InjectionConfig.get();

        if (!config.isEnabled() || isMissingRequiredParameters(config)) {
            return false;
        }

        Set<String> disabledNodes =
            CollectionUtil.safeStream(config.getGradleInjectionDisabledNodes())
                .map(NodeLabelItem::getLabel)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> enabledNodes =
            CollectionUtil.safeStream(config.getGradleInjectionEnabledNodes())
                .map(NodeLabelItem::getLabel)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return InjectionUtil.isInjectionEnabledForNode(node::getAssignedLabels, disabledNodes, enabledNodes);
    }

    private static boolean isMissingRequiredParameters(InjectionConfig config) {
        String server = config.getServer();
        String gradlePluginVersion = config.getGradlePluginVersion();

        return InjectionUtil.isAnyInvalid(
            InjectionConfig.checkRequiredUrl(server),
            InjectionConfig.checkRequiredVersion(gradlePluginVersion)
        );
    }

    @Override
    public void inject(Node node, EnvVars envGlobal, EnvVars envComputer) {
        boolean enabled = isEnabled(node);

        try {
            String initScriptDirectory = getInitScriptDirectory(envGlobal, envComputer);

            if (enabled) {
                inject(node, initScriptDirectory);
            } else {
                cleanup(node, initScriptDirectory);
            }
        } catch (IllegalStateException e) {
            if (enabled) {
                LOGGER.log(Level.WARNING, "Unexpected exception while injecting build scans for Gradle", e);
            }
        }
    }

    private static String getInitScriptDirectory(EnvVars envGlobal, EnvVars envComputer) {
        String gradleHomeOverride = EnvUtil.getEnv(envGlobal, JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME);
        String homeOverride = EnvUtil.getEnv(envGlobal, JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_HOME);

        if (gradleHomeOverride != null) {
            return filePath(gradleHomeOverride, INIT_DIR);
        } else if (homeOverride != null) {
            return filePath(homeOverride, GRADLE_DIR, INIT_DIR);
        } else {
            String home = EnvUtil.getEnv(envComputer, HOME);
            Preconditions.checkState(home != null, "HOME is not set");

            return filePath(home, GRADLE_DIR, INIT_DIR);
        }
    }

    private void inject(Node node, String initScriptDirectory) {
        try {
            injectInitScript(node.getChannel(), initScriptDirectory);
            injectEnvironmentVariables(node);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private void injectInitScript(VirtualChannel channel, String initScriptDirectory) throws IOException, InterruptedException {
        FilePath gradleInitScriptFile = getInitScriptFile(channel, initScriptDirectory);
        if (initScriptChanged(gradleInitScriptFile)) {
            LOGGER.info("Injecting Gradle init script " + gradleInitScriptFile);

            copyResourceToNode(gradleInitScriptFile, RESOURCE_INIT_SCRIPT_GRADLE);
        }
    }

    private void injectEnvironmentVariables(Node node) {
        InjectionConfig config = InjectionConfig.get();

        EnvUtil.setEnvVar(node, JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL, config.getServer());
        EnvUtil.setEnvVar(node, JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION, config.getGradlePluginVersion());

        if (config.isAllowUntrusted()) {
            EnvUtil.setEnvVar(node, JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER, "true");
        }

        String pluginRepositoryUrl = config.getGradlePluginRepositoryUrl();
        if (pluginRepositoryUrl != null && InjectionUtil.isValid(InjectionConfig.checkUrl(pluginRepositoryUrl))) {
            EnvUtil.setEnvVar(node, JENKINSGRADLEPLUGIN_GRADLE_PLUGIN_REPOSITORY_URL, pluginRepositoryUrl);
        }
        String ccudPluginVersion = config.getCcudPluginVersion();
        if (ccudPluginVersion != null && InjectionUtil.isValid(InjectionConfig.checkVersion(ccudPluginVersion))) {
            EnvUtil.setEnvVar(node, JENKINSGRADLEPLUGIN_CCUD_PLUGIN_VERSION, ccudPluginVersion);
        }
    }

    private boolean initScriptChanged(FilePath gradleInitScriptFile) throws IOException, InterruptedException {
        if (!gradleInitScriptFile.exists()) {
            return true;
        }
        String existingFileDigest = gradleInitScriptFile.digest();
        return !Objects.equals(existingFileDigest, initScriptDigest.get());
    }

    private void cleanup(Node node, String initScriptDirectory) {
        try {
            removeInitScript(node.getChannel(), initScriptDirectory);
            EnvUtil.removeEnvVars(node, ALL_INJECTED_ENVIRONMENT_VARIABLES);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private void removeInitScript(VirtualChannel channel, String initScriptDirectory) throws IOException, InterruptedException {
        FilePath gradleInitScriptFile = getInitScriptFile(channel, initScriptDirectory);
        if (gradleInitScriptFile.exists()) {
            LOGGER.info("Deleting Gradle init script " + gradleInitScriptFile.getRemote());

            boolean deleted = gradleInitScriptFile.delete();
            Preconditions.checkState(deleted, "Error while deleting init script");
        }
    }

    private static FilePath getInitScriptFile(VirtualChannel channel, String initScriptDirectory) {
        Preconditions.checkState(initScriptDirectory != null, "init script directory is null");

        return new FilePath(channel, filePath(initScriptDirectory, GRADLE_INIT_FILE));
    }

    private static String filePath(String... parts) {
        return String.join("/", parts);
    }
}
