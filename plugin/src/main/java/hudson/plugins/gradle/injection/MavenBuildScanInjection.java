package hudson.plugins.gradle.injection;

import hudson.FilePath;
import hudson.model.Node;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.plugins.gradle.injection.MavenExtClasspathUtils.constructExtClasspath;
import static hudson.plugins.gradle.injection.MavenExtClasspathUtils.isUnix;

public class MavenBuildScanInjection implements MavenInjectionAware {

    private static final Logger LOGGER = Logger.getLogger(MavenBuildScanInjection.class.getName());

    public static final String JENKINSGRADLEPLUGIN_MAVEN_AUTO_INJECTION = "JENKINSGRADLEPLUGIN_MAVEN_AUTO_INJECTION";

    // MAVEN_OPTS is handled separately
    private static final List<String> ALL_INJECTED_ENVIRONMENT_VARIABLES =
            Arrays.asList(
                    JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH,
                    JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL,
                    JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER,
                    JENKINSGRADLEPLUGIN_MAVEN_AUTO_INJECTION
            );

    private final MavenExtensionsHandler extensionsHandler = new MavenExtensionsHandler();

    public void inject(Node node, Map<MavenExtension, String> extensionsDigest) {
        if (node == null) {
            return;
        }

        FilePath nodeRootPath = node.getRootPath();
        if (nodeRootPath == null) {
            return;
        }

        InjectionConfig config = InjectionConfig.get();
        boolean enabled = isInjectionEnabledForNode(config, node);
        try {
            if (enabled) {
                if (!extensionsDigest.isEmpty()) {
                    inject(config, node, nodeRootPath, extensionsDigest);
                } else {
                    LOGGER.log(Level.WARNING, "Extension digests are not present even though injection is enabled");
                }
            } else {
                cleanup(node, nodeRootPath);
            }
        } catch (IllegalStateException e) {
            if (enabled) {
                LOGGER.log(Level.WARNING, "Unexpected exception while injecting build scans for Maven", e);
            }
        }
    }

    private void inject(InjectionConfig config, Node node, FilePath nodeRootPath, Map<MavenExtension, String> extensionsDigest) {
        try {
            EnvUtil.setEnvVar(node, JENKINSGRADLEPLUGIN_MAVEN_AUTO_INJECTION, "true");

            String server = config.getServer();

            LOGGER.info("Injecting Maven extensions " + nodeRootPath);

            List<FilePath> extensions = new ArrayList<>();
            FilePath controllerRootPath = Jenkins.get().getRootPath();

            MavenExtension develocityMavenExtension = MavenExtension.forVersion(config.getMavenExtensionVersion());
            extensions.add(extensionsHandler.copyExtensionToAgent(develocityMavenExtension, controllerRootPath, nodeRootPath, extensionsDigest.get(develocityMavenExtension)));
            if (InjectionUtil.isInvalid(InjectionConfig.checkRequiredVersion(config.getCcudExtensionVersion()))) {
                extensionsHandler.deleteExtensionFromAgent(MavenExtension.CCUD, nodeRootPath);
            } else {
                extensions.add(extensionsHandler.copyExtensionToAgent(MavenExtension.CCUD, controllerRootPath, nodeRootPath, extensionsDigest.get(MavenExtension.CCUD)));
            }

            boolean isUnix = isUnix(node);

            List<SystemProperty> systemProperties = new ArrayList<>();
            systemProperties.add(new SystemProperty(MAVEN_EXT_CLASS_PATH_PROPERTY_KEY, constructExtClasspath(extensions, isUnix)));
            systemProperties.add(new SystemProperty(DEVELOCITY_UPLOAD_IN_BACKGROUND_PROPERTY_KEY, "false"));
            systemProperties.add(new SystemProperty(BUILD_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY, "false"));

            systemProperties.add(new SystemProperty(DEVELOCITY_URL_PROPERTY_KEY, server));
            systemProperties.add(new SystemProperty(GRADLE_ENTERPRISE_URL_PROPERTY_KEY, server));
            if (config.isAllowUntrusted()) {
                systemProperties.add(new SystemProperty(DEVELOCITY_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY, "true"));
                systemProperties.add(new SystemProperty(GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY, "true"));
            }

            String captureFileFingerprints = Optional.ofNullable(config.isMavenCaptureGoalInputFiles()).map(b -> Boolean.toString(b)).orElse("true");
            systemProperties.add(new SystemProperty(DEVELOCITY_CAPTURE_FILE_FINGERPRINTS_PROPERTY_KEY, captureFileFingerprints));
            systemProperties.add(new SystemProperty(GRADLE_ENTERPRISE_CAPTURE_GOAL_INPUT_FILES_PROPERTY_KEY, captureFileFingerprints));

            EnvUtil.setEnvVar(node, MavenOptsHandler.MAVEN_OPTS, MAVEN_OPTS_HANDLER.merge(node, systemProperties));

            // Configuration needed to support https://plugins.jenkins.io/maven-plugin/
            extensions.add(extensionsHandler.copyExtensionToAgent(MavenExtension.CONFIGURATION, nodeRootPath));

            EnvUtil.setEnvVar(node, JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH, constructExtClasspath(extensions, isUnix));
            EnvUtil.setEnvVar(node, JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL, config.getServer());
            if (config.isAllowUntrusted()) {
                EnvUtil.setEnvVar(node, JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER, "true");
            } else {
                EnvUtil.removeEnvVar(node, JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER);
            }
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private void cleanup(Node node, FilePath rootPath) {
        try {
            extensionsHandler.deleteAllExtensionsFromAgent(rootPath);

            MAVEN_OPTS_HANDLER.removeIfNeeded(node);
            EnvUtil.removeEnvVars(node, ALL_INJECTED_ENVIRONMENT_VARIABLES);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
