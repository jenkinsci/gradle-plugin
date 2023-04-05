package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.plugins.gradle.injection.MavenExtensionsHandler.MavenExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MavenBuildScanInjection implements BuildScanInjection, MavenInjectionAware {

    private static final Logger LOGGER = Logger.getLogger(MavenBuildScanInjection.class.getName());

    public static final String JENKINSGRADLEPLUGIN_MAVEN_AUTO_INJECTION = "JENKINSGRADLEPLUGIN_MAVEN_AUTO_INJECTION";

    // MAVEN_OPTS is handled separately
    private static final List<String> ALL_INJECTED_ENVIRONMENT_VARIABLES =
        Arrays.asList(
            JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH,
            JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL,
            JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER,
            JENKINSGRADLEPLUGIN_MAVEN_AUTO_INJECTION,
            JENKINSGRADLEPLUGIN_MAVEN_OPTS_PREPARED,
            JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH_PREPARED
        );

    private final MavenExtensionsHandler extensionsHandler = new MavenExtensionsHandler();

    @Override
    public void inject(Node node, EnvVars envGlobal, EnvVars envComputer) {
        if (node == null) {
            return;
        }

        FilePath nodeRootPath = node.getRootPath();
        if (nodeRootPath == null) {
            return;
        }

        boolean enabled = isInjectionEnabledForNode(node);
        try {
            if (enabled) {
                inject(node, nodeRootPath);
            } else {
                cleanup(node, nodeRootPath);
            }
        } catch (IllegalStateException e) {
            if (enabled) {
                LOGGER.log(Level.WARNING, "Unexpected exception while injecting build scans for Maven", e);
            }
        }
    }

    private void inject(Node node, FilePath nodeRootPath) {
        try {
            EnvUtil.setEnvVar(node, JENKINSGRADLEPLUGIN_MAVEN_AUTO_INJECTION, "true");

            InjectionConfig config = InjectionConfig.get();
            String server = config.getServer();

            LOGGER.info("Injecting Maven extensions " + nodeRootPath);

            List<FilePath> extensions = new ArrayList<>();
            extensions.add(extensionsHandler.copyExtensionToAgent(MavenExtension.GRADLE_ENTERPRISE, nodeRootPath));
            if (config.isInjectCcudExtension()) {
                extensions.add(extensionsHandler.copyExtensionToAgent(MavenExtension.CCUD, nodeRootPath));
            } else {
                extensionsHandler.deleteExtensionFromAgent(MavenExtension.CCUD, nodeRootPath);
            }

            boolean isUnix = isUnix(node);

            List<SystemProperty> systemProperties = new ArrayList<>();
            systemProperties.add(new SystemProperty(MAVEN_EXT_CLASS_PATH_PROPERTY_KEY, constructExtClasspath(extensions, isUnix)));
            systemProperties.add(new SystemProperty(BUILD_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY, "false"));

            systemProperties.add(new SystemProperty(GRADLE_ENTERPRISE_URL_PROPERTY_KEY, server));
            if (config.isAllowUntrusted()) {
                systemProperties.add(new SystemProperty(GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY, "true"));
            }

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

            // We still need to clean up MAVEN_OPTS and Maven plugin variables set in older
            // versions even though we now set it in EnvironmentContributor.
            // This behavior is temporary and can be deleted at some point in the future
            MAVEN_OPTS_HANDLER.removeIfNeeded(node);
            EnvUtil.removeEnvVars(node, ALL_INJECTED_ENVIRONMENT_VARIABLES);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String constructExtClasspath(List<FilePath> extensions, boolean isUnix) {
        return extensions
                .stream()
                .map(FilePath::getRemote)
                .collect(Collectors.joining(getDelimiter(isUnix)));
    }

    private static String getDelimiter(boolean isUnix) {
        return isUnix ? ":" : ";";
    }

    private static boolean isUnix(Node node) {
        Computer computer = node.toComputer();

        return computer == null || Boolean.TRUE.equals(computer.isUnix());
    }

}
