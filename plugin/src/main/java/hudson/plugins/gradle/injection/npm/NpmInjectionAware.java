package hudson.plugins.gradle.injection.npm;

import hudson.plugins.gradle.injection.InjectionAware;
import hudson.plugins.gradle.injection.InjectionConfig;
import hudson.plugins.gradle.injection.NodeLabelItem;

import javax.annotation.Nullable;
import java.util.List;

public interface NpmInjectionAware extends InjectionAware {

    @Nullable
    @Override
    default String getAgentVersion(InjectionConfig config) {
        return config.getNpmAgentVersion();
    }

    @Nullable
    @Override
    default List<NodeLabelItem> getAgentInjectionDisabledNodes(InjectionConfig config) {
        return config.getNpmInjectionDisabledNodes();
    }

    @Nullable
    @Override
    default List<NodeLabelItem> getAgentInjectionEnabledNodes(InjectionConfig config) {
        return config.getNpmInjectionEnabledNodes();
    }
}
