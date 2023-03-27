package hudson.plugins.gradle.injection;

import hudson.model.Node;
import hudson.plugins.gradle.util.CollectionUtil;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public interface GradleInjectionAware {

    // Environment variable that enables init script logic when set
    String GRADLE_ENTERPRISE_GRADLE_INJECTION_ENABLED = "GRADLE_ENTERPRISE_GRADLE_INJECTION_ENABLED";

    String JENKINSGRADLEPLUGIN_GRADLE_AUTO_INJECTION = "JENKINSGRADLEPLUGIN_GRADLE_AUTO_INJECTION";

    String JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL = "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL";
    String JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER = "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER";
    String JENKINSGRADLEPLUGIN_GRADLE_PLUGIN_REPOSITORY_URL = "JENKINSGRADLEPLUGIN_GRADLE_PLUGIN_REPOSITORY_URL";
    String JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION = "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION";
    String JENKINSGRADLEPLUGIN_CCUD_PLUGIN_VERSION = "JENKINSGRADLEPLUGIN_CCUD_PLUGIN_VERSION";

    default boolean isInjectionEnabled(Node node) {
        InjectionConfig config = InjectionConfig.get();

        boolean anyRequiredPropertyInvalid = InjectionUtil.isAnyInvalid(
                InjectionConfig.checkRequiredUrl(config.getServer()),
                InjectionConfig.checkRequiredVersion(config.getGradlePluginVersion())
        );

        if (config.isDisabled() || anyRequiredPropertyInvalid) {
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

}
