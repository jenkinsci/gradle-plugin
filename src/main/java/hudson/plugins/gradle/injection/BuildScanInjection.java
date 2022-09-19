package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public interface BuildScanInjection {

    default boolean injectionEnabled(EnvVars env) {
        return EnvUtil.isSet(env, getActivationEnvironmentVariableName());
    }

    String getActivationEnvironmentVariableName();

    void inject(Node node, EnvVars envGlobal, EnvVars envComputer);

    default boolean injectionEnabledForNode(Node node, EnvVars envGlobal) {
        if (!EnvUtil.isSet(envGlobal, getActivationEnvironmentVariableName())) {
            return false;
        }

        Set<String> labels =
            (node.getAssignedLabels() != null ? node.getAssignedLabels() : Collections.<LabelAtom>emptySet())
                .stream()
                .map(LabelAtom::getName)
                .collect(Collectors.toSet());

        String disabledNodes = EnvUtil.getEnv(envGlobal, getDisabledNodesEnvironmentVariableName());
        String enabledNodes = EnvUtil.getEnv(envGlobal, getEnabledNodesEnvironmentVariableName());

        return InjectionUtils.isInjectionEnabledForNode(labels, disabledNodes, enabledNodes);
    }

    String getEnabledNodesEnvironmentVariableName();

    String getDisabledNodesEnvironmentVariableName();
}
