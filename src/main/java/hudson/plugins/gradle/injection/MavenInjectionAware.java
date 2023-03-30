package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.model.InvisibleAction;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.gradle.util.CollectionUtil;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public interface MavenInjectionAware {

    String JENKINSGRADLEPLUGIN_MAVEN_OPTS_PREPARED = "JENKINSGRADLEPLUGIN_MAVEN_OPTS_PREPARED";
    String JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH_PREPARED = "JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH_PREPARED";

    String JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH = "JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH";
    // Use different variables so Gradle and Maven injections can work independently on the same node
    String JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL = "JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL";
    String JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER = "JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER";

    // Maven system properties passed on the CLI to a Maven build
    SystemProperty.Key GRADLE_ENTERPRISE_URL_PROPERTY_KEY = SystemProperty.Key.required("gradle.enterprise.url");
    SystemProperty.Key BUILD_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY = SystemProperty.Key.required("gradle.scan.uploadInBackground");
    SystemProperty.Key MAVEN_EXT_CLASS_PATH_PROPERTY_KEY = SystemProperty.Key.required("maven.ext.class.path");
    SystemProperty.Key GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY = SystemProperty.Key.optional("gradle.enterprise.allowUntrustedServer");

    MavenOptsHandler MAVEN_OPTS_HANDLER = new MavenOptsHandler(
            MAVEN_EXT_CLASS_PATH_PROPERTY_KEY,
            BUILD_SCAN_UPLOAD_IN_BACKGROUND_PROPERTY_KEY,
            GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER_PROPERTY_KEY,
            GRADLE_ENTERPRISE_URL_PROPERTY_KEY
    );

    default boolean isInjectionDisabledGlobally(InjectionConfig config) {
        if (config.isDisabled() || !config.isInjectMavenExtension()) {
            return true;
        }

        return InjectionUtil.isInvalid(InjectionConfig.checkRequiredUrl(config.getServer()));
    }

    default boolean isInjectionEnabledForNode(Node node) {
        InjectionConfig config = InjectionConfig.get();

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

    default void setPreparedMavenProperties(Run<?, ?> run, TaskListener listener) {
        try {
            EnvVars environment = run.getEnvironment(listener);

            String preparedMavenOpts = environment.get(JENKINSGRADLEPLUGIN_MAVEN_OPTS_PREPARED);
            String preparedMavenPluginConfigExtClasspath = environment.get(JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH_PREPARED);
            if (preparedMavenOpts != null && preparedMavenPluginConfigExtClasspath != null) {
                run.addAction(new PreparedMavenProperties(preparedMavenOpts, preparedMavenPluginConfigExtClasspath));
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Action that holds Maven properties to be set in EnvironmentContributor
     */
    class PreparedMavenProperties extends InvisibleAction {

        public final String preparedMavenOpts;
        public final String preparedMavenPluginConfigExtClasspath;

        public PreparedMavenProperties(String preparedMavenOpts, String preparedMavenPluginConfigExtClasspath) {
            this.preparedMavenOpts = preparedMavenOpts;
            this.preparedMavenPluginConfigExtClasspath = preparedMavenPluginConfigExtClasspath;
        }
    }

}
