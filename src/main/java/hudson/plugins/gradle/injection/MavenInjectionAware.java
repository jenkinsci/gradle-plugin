package hudson.plugins.gradle.injection;

import hudson.model.Node;
import hudson.plugins.gradle.util.CollectionUtil;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public interface MavenInjectionAware {

    String JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH = "JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH";
    // Use different variables so Gradle and Maven injections can work independently on the same node
    String JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL = "JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL";
    String JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER = "JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER";

    // Maven system properties passed on the CLI to a Maven build
    SystemProperty.Key DEVELOCITY_URL_PROPERTY_KEY = SystemProperty.Key.required("develocity.url");
    SystemProperty.Key GRADLE_ENTERPRISE_URL_PROPERTY_KEY = SystemProperty.Key.required("gradle.enterprise.url");
    SystemProperty.Key BUILD_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY = SystemProperty.Key.required("gradle.scan.uploadInBackground");
    SystemProperty.Key DEVELOCITY_UPLOAD_IN_BACKGROUND_PROPERTY_KEY = SystemProperty.Key.required("develocity.scan.uploadInBackground");
    SystemProperty.Key MAVEN_EXT_CLASS_PATH_PROPERTY_KEY = SystemProperty.Key.required("maven.ext.class.path");
    SystemProperty.Key GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY = SystemProperty.Key.optional("gradle.enterprise.allowUntrustedServer");
    SystemProperty.Key DEVELOCITY_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY = SystemProperty.Key.optional("develocity.allowUntrustedServer");
    SystemProperty.Key GRADLE_ENTERPRISE_CAPTURE_GOAL_INPUT_FILES_PROPERTY_KEY = SystemProperty.Key.optional("gradle.scan.captureGoalInputFiles");
    SystemProperty.Key DEVELOCITY_CAPTURE_FILE_FINGERPRINTS_PROPERTY_KEY = SystemProperty.Key.optional("develocity.scan.captureFileFingerprints");

    MavenOptsHandler MAVEN_OPTS_HANDLER = new MavenOptsHandler(
            MAVEN_EXT_CLASS_PATH_PROPERTY_KEY,
            BUILD_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY,
            GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY,
            GRADLE_ENTERPRISE_URL_PROPERTY_KEY,
            GRADLE_ENTERPRISE_CAPTURE_GOAL_INPUT_FILES_PROPERTY_KEY,
            DEVELOCITY_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY,
            DEVELOCITY_UPLOAD_IN_BACKGROUND_PROPERTY_KEY,
            DEVELOCITY_URL_PROPERTY_KEY,
            DEVELOCITY_CAPTURE_FILE_FINGERPRINTS_PROPERTY_KEY
    );

    default boolean isInjectionDisabledGlobally(InjectionConfig config) {
        return config.isDisabled() ||
                InjectionUtil.isAnyInvalid(
                        InjectionConfig.checkRequiredUrl(config.getServer()),
                        InjectionConfig.checkRequiredVersion(config.getMavenExtensionVersion())
                );
    }

    default boolean isInjectionEnabledForNode(InjectionConfig config, Node node) {
        if (isInjectionDisabledGlobally(config)) {
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

}
