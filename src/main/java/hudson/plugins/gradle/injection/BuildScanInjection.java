package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.plugins.gradle.util.CollectionUtil;
import hudson.util.FormValidation;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public interface BuildScanInjection {

    boolean isEnabled(Node node);

    void inject(Node node, EnvVars envGlobal, EnvVars envComputer);

    static boolean isNoOk(FormValidation validation) {
        return !isOk(validation);
    }

    static boolean isOk(FormValidation validation) {
        return validation.kind == FormValidation.Kind.OK;
    }

    static boolean isAnyNotOk(FormValidation... validations) {
        return Arrays.stream(validations).anyMatch(v -> v.kind != FormValidation.Kind.OK);
    }

    static boolean isInjectionEnabledForNode(Node node, Set<String> disabledNodes, Set<String> enabledNodes) {
        Set<String> labels =
            CollectionUtil.safeStream(node.getAssignedLabels())
                .map(LabelAtom::getName)
                .collect(Collectors.toSet());

        return isNotDisabled(labels, disabledNodes) && isEnabled(labels, enabledNodes);
    }

    static boolean isNotDisabled(Set<String> labels, Set<String> disabledNodes) {
        if (disabledNodes.isEmpty()) {
            return true;
        }
        return labels.stream().noneMatch(disabledNodes::contains);
    }

    static boolean isEnabled(Set<String> labels, Set<String> enabledNodes) {
        if (enabledNodes.isEmpty()) {
            return true;
        }
        return enabledNodes.stream().anyMatch(labels::contains);
    }
}
