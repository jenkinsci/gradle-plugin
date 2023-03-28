package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Node;
import hudson.plugins.gradle.injection.MavenExtensionsHandler.MavenExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MavenBuildScanInjection implements BuildScanInjection, MavenInjectionAware {

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

    @Override
    public void inject(Node node, EnvVars envGlobal, EnvVars envComputer) {
        if (node == null) {
            return;
        }

        FilePath nodeRootPath = node.getRootPath();
        if (nodeRootPath == null) {
            return;
        }

        boolean enabled = isInjectionEnabled(node);
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

            LOGGER.info("Injecting Maven extensions " + nodeRootPath);

            extensionsHandler.copyExtensionToAgent(MavenExtension.GRADLE_ENTERPRISE, nodeRootPath);
            if (config.isInjectCcudExtension()) {
                extensionsHandler.copyExtensionToAgent(MavenExtension.CCUD, nodeRootPath);
            } else {
                extensionsHandler.deleteExtensionFromAgent(MavenExtension.CCUD, nodeRootPath);
            }

            // Configuration needed to support https://plugins.jenkins.io/maven-plugin/
            extensionsHandler.copyExtensionToAgent(MavenExtension.CONFIGURATION, nodeRootPath);
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

}
