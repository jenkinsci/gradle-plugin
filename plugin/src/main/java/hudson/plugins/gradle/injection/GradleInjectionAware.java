package hudson.plugins.gradle.injection;

import java.util.List;

public interface GradleInjectionAware extends InjectionAware {

    @Override
    default String getAgentVersion(InjectionConfig config) {
        return config.getGradlePluginVersion();
    }

    @Override
    default List<NodeLabelItem> getAgentInjectionDisabledNodes(InjectionConfig config) {
        return config.getGradleInjectionDisabledNodes();
    }

    @Override
    default List<NodeLabelItem> getAgentInjectionEnabledNodes(InjectionConfig config) {
        return config.getGradleInjectionEnabledNodes();
    }
}
