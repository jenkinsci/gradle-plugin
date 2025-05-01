package hudson.plugins.gradle.injection;

import hudson.model.Node;
import hudson.plugins.gradle.util.CollectionUtil;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public interface GradleInjectionAware {

    default boolean isInjectionDisabledGlobally(InjectionConfig config) {
        return config.isDisabled()
                || InjectionUtil.isAnyInvalid(
                        InjectionConfig.checkRequiredUrl(config.getServer()),
                        InjectionConfig.checkRequiredVersion(config.getGradlePluginVersion()));
    }

    default boolean isInjectionEnabledForNode(InjectionConfig config, Node node) {
        if (isInjectionDisabledGlobally(config)) {
            return false;
        }

        Set<String> disabledNodes = CollectionUtil.safeStream(config.getGradleInjectionDisabledNodes())
                .map(NodeLabelItem::getLabel)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> enabledNodes = CollectionUtil.safeStream(config.getGradleInjectionEnabledNodes())
                .map(NodeLabelItem::getLabel)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return InjectionUtil.isInjectionEnabledForNode(node::getAssignedLabels, disabledNodes, enabledNodes);
    }
}
