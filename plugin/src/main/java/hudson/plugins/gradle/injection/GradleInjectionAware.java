package hudson.plugins.gradle.injection;

import javax.annotation.Nullable;
import java.util.List;

public interface GradleInjectionAware extends InjectionAware {

    @Nullable
    @Override
    default String getAgentVersion(InjectionConfig config) {
        return config.getGradlePluginVersion();
    }

    @Nullable
    @Override
    default List<NodeLabelItem> getAgentInjectionDisabledNodes(InjectionConfig config) {
        return config.getGradleInjectionDisabledNodes();
    }

    @Nullable
    @Override
    default List<NodeLabelItem> getAgentInjectionEnabledNodes(InjectionConfig config) {
        return config.getGradleInjectionEnabledNodes();
    }
}
