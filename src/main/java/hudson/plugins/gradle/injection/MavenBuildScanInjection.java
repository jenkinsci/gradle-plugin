package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.plugins.gradle.injection.MavenExtensionsHandler.MavenExtension;
import hudson.plugins.gradle.util.CollectionUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MavenBuildScanInjection implements BuildScanInjection {

    private static final Logger LOGGER = Logger.getLogger(MavenBuildScanInjection.class.getName());

    private static final String JENKINSGRADLEPLUGIN_MAVEN_AUTO_INJECTION = "JENKINSGRADLEPLUGIN_MAVEN_AUTO_INJECTION";

    public static final String JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH = "JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH";
    // Use different variables so Gradle and Maven injections can work independently on the same node
    public static final String JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL = "JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL";
    public static final String JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER = "JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER";

    // Maven system properties passed on the CLI to a Maven build
    private static final SystemProperty.Key GRADLE_ENTERPRISE_URL_PROPERTY_KEY = SystemProperty.Key.required("gradle.enterprise.url");
    private static final SystemProperty.Key BUILD_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY = SystemProperty.Key.required("gradle.scan.uploadInBackground");
    private static final SystemProperty.Key MAVEN_EXT_CLASS_PATH_PROPERTY_KEY = SystemProperty.Key.required("maven.ext.class.path");
    private static final SystemProperty.Key GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY = SystemProperty.Key.optional("gradle.enterprise.allowUntrustedServer");

    private static final MavenOptsSetter MAVEN_OPTS_SETTER = new MavenOptsSetter(
        MAVEN_EXT_CLASS_PATH_PROPERTY_KEY,
        BUILD_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY,
        GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY,
        GRADLE_ENTERPRISE_URL_PROPERTY_KEY
    );

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
    public boolean isEnabled(Node node) {
        InjectionConfig config = InjectionConfig.get();

        if (config.isDisabled() || !config.isInjectMavenExtension() || isMissingRequiredParameters(config)) {
            return false;
        }

        Set<String> disabledNodes =
            CollectionUtil.safeStream(config.getMavenInjectionDisabledNodes())
                .map(NodeLabelItem::getLabel)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> enabledNodes =
            CollectionUtil.safeStream(config.getMavenInjectionEnabledNodes())
                .map(NodeLabelItem::getLabel)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return InjectionUtil.isInjectionEnabledForNode(node::getAssignedLabels, disabledNodes, enabledNodes);
    }

    private static boolean isMissingRequiredParameters(InjectionConfig config) {
        String server = config.getServer();

        return InjectionUtil.isInvalid(InjectionConfig.checkRequiredUrl(server));
    }

    @Override
    public void inject(Node node, EnvVars envGlobal, EnvVars envComputer) {
        boolean enabled = isEnabled(node);

        try {
            if (node == null) {
                return;
            }

            FilePath nodeRootPath = node.getRootPath();
            if (nodeRootPath == null) {
                return;
            }

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

            List<FilePath> extensions = new LinkedList<>();
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

            MAVEN_OPTS_SETTER.append(node, systemProperties);

            // Configuration needed to support https://plugins.jenkins.io/maven-plugin/
            extensions.add(extensionsHandler.copyExtensionToAgent(MavenExtension.CONFIGURATION, nodeRootPath));

            EnvUtil.setEnvVar(node, JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH, constructExtClasspath(extensions, isUnix));
            EnvUtil.setEnvVar(node, JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL, server);
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
            MAVEN_OPTS_SETTER.removeIfNeeded(node);
            EnvUtil.removeEnvVars(node, ALL_INJECTED_ENVIRONMENT_VARIABLES);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String constructExtClasspath(List<FilePath> extensions,
                                                boolean isUnix) throws IOException, InterruptedException {
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
