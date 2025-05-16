package hudson.plugins.gradle.injection;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernameCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.plugins.gradle.injection.CopyUtil.*;

public class GradleBuildScanInjection implements GradleInjectionAware {

    private static final Logger LOGGER = Logger.getLogger(GradleBuildScanInjection.class.getName());

    private static final String JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME = "JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME";
    private static final String JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_HOME = "JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_HOME";
    private static final String HOME = "HOME";

    @VisibleForTesting
    static final String RESOURCE_INIT_SCRIPT_GRADLE = "init-script.gradle";
    private static final String INIT_DIR = "init.d";
    private static final String GRADLE_DIR = ".gradle";
    private static final String GRADLE_INIT_FILE = "init-build-scan.gradle";

    private final Supplier<String> initScriptDigest = Suppliers.memoize(() -> unsafeResourceDigest(RESOURCE_INIT_SCRIPT_GRADLE));

    public void inject(Node node, EnvVars envGlobal, EnvVars envComputer) {
        if (node == null) {
            return;
        }

        InjectionConfig config = InjectionConfig.get();
        boolean enabled = isInjectionEnabledForNode(config, node);
        try {
            String initScriptDirectory = getInitScriptDirectory(envGlobal, envComputer);

            if (enabled) {
                inject(config, node, initScriptDirectory);
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

    private void inject(InjectionConfig config, Node node, String initScriptDirectory) {
        try {
            EnvUtil.setEnvVar(node, InitScriptVariables.DEVELOCITY_INJECTION_ENABLED, "true");
            EnvUtil.setEnvVar(node, InitScriptVariables.DEVELOCITY_INJECTION_DEBUG, "true");
            injectInitScript(node.getChannel(), initScriptDirectory);
            injectEnvironmentVariables(config, node);
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

    private void injectEnvironmentVariables(InjectionConfig config, Node node) {
        EnvUtil.setEnvVar(node, InitScriptVariables.DEVELOCITY_URL, config.getServer());
        EnvUtil.setEnvVar(node, InitScriptVariables.DEVELOCITY_PLUGIN_VERSION, config.getGradlePluginVersion());
        EnvUtil.setEnvVar(node, InitScriptVariables.DEVELOCITY_INIT_SCRIPT_NAME, GRADLE_INIT_FILE);
        EnvUtil.setEnvVar(node, InitScriptVariables.DEVELOCITY_INJECTION_CUSTOM_VALUE, "Jenkins");

        if (config.isAllowUntrusted()) {
            EnvUtil.setEnvVar(node, InitScriptVariables.DEVELOCITY_ALLOW_UNTRUSTED_SERVER, "true");
        } else {
            EnvUtil.removeEnvVar(node, InitScriptVariables.DEVELOCITY_ALLOW_UNTRUSTED_SERVER);
        }

        if (config.isEnforceUrl()) {
            EnvUtil.setEnvVar(node, InitScriptVariables.DEVELOCITY_ENFORCE_URL, "true");
        } else {
            EnvUtil.removeEnvVar(node, InitScriptVariables.DEVELOCITY_ENFORCE_URL);
        }

        if (config.isGradleCaptureTaskInputFiles() == null || config.isGradleCaptureTaskInputFiles()) {
            EnvUtil.setEnvVar(node, InitScriptVariables.DEVELOCITY_CAPTURE_FILE_FINGERPRINTS, "true");
        } else {
            EnvUtil.removeEnvVar(node, InitScriptVariables.DEVELOCITY_CAPTURE_FILE_FINGERPRINTS);
        }

        String pluginRepositoryUrl = config.getGradlePluginRepositoryUrl();
        if (pluginRepositoryUrl != null && InjectionUtil.isValid(InjectionConfig.checkUrl(pluginRepositoryUrl))) {
            EnvUtil.setEnvVar(node, InitScriptVariables.GRADLE_PLUGIN_REPOSITORY_URL, pluginRepositoryUrl);
        }

        String repositoryUsername = getRepositoryUsername(config.getGradlePluginRepositoryCredentialId());
        if (repositoryUsername != null) {
            EnvUtil.setEnvVar(node, InitScriptVariables.GRADLE_PLUGIN_REPOSITORY_USERNAME, repositoryUsername);
        } else {
            EnvUtil.removeEnvVar(node, InitScriptVariables.GRADLE_PLUGIN_REPOSITORY_USERNAME);
        }

        String ccudPluginVersion = config.getCcudPluginVersion();
        if (ccudPluginVersion != null && InjectionUtil.isValid(InjectionConfig.checkVersion(ccudPluginVersion))) {
            EnvUtil.setEnvVar(node, InitScriptVariables.CCUD_PLUGIN_VERSION, ccudPluginVersion);
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

            EnvUtil.removeEnvVars(node, InitScriptVariables.values());
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

    private static String getRepositoryUsername(String gradlePluginRepositoryCredentialId) {
        List<StandardUsernamePasswordCredentials> allCredentials
                = CredentialsProvider.lookupCredentialsInItem(StandardUsernamePasswordCredentials.class, null, null);

        return allCredentials.stream()
                .filter(it -> it.getId().equals(gradlePluginRepositoryCredentialId))
                .findFirst()
                .map(UsernameCredentials::getUsername)
                .orElse(null);
    }

    private static String filePath(String... parts) {
        return String.join("/", parts);
    }

}
