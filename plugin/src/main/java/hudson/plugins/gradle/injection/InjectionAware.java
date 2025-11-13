package hudson.plugins.gradle.injection;

import hudson.model.Node;
import hudson.plugins.gradle.util.CollectionUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public interface InjectionAware {

    @Nullable
    String getAgentVersion(InjectionConfig config);

    @Nullable
    List<NodeLabelItem> getAgentInjectionDisabledNodes(InjectionConfig config);

    @Nullable
    List<NodeLabelItem> getAgentInjectionEnabledNodes(InjectionConfig config);

    default boolean isInjectionDisabledGlobally(InjectionConfig config) {
        return config.isDisabled() ||
                InjectionUtil.isAnyInvalid(
                        InjectionConfig.checkRequiredUrl(config.getServer()),
                        InjectionConfig.checkRequiredVersion(getAgentVersion(config))
                );
    }

    default boolean isInjectionEnabledForNode(InjectionConfig config, Node node) {
        if (isInjectionDisabledGlobally(config)) {
            return false;
        }

        Set<String> disabledNodes =
                CollectionUtil.safeStream(getAgentInjectionDisabledNodes(config))
                        .map(NodeLabelItem::getLabel)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        Set<String> enabledNodes =
                CollectionUtil.safeStream(getAgentInjectionEnabledNodes(config))
                        .map(NodeLabelItem::getLabel)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        return InjectionUtil.isInjectionEnabledForNode(node::getAssignedLabels, disabledNodes, enabledNodes);
    }
}
