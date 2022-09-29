package hudson.plugins.gradle.injection;

import hudson.model.labels.LabelAtom;
import hudson.plugins.gradle.util.CollectionUtil;
import hudson.util.FormValidation;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class InjectionUtil {

    private InjectionUtil() {
    }

    public static boolean isNoOk(FormValidation validation) {
        return !isOk(validation);
    }

    public static boolean isOk(FormValidation validation) {
        return validation.kind == FormValidation.Kind.OK;
    }

    public static boolean isAnyNotOk(FormValidation... validations) {
        return Arrays.stream(validations).anyMatch(v -> v.kind != FormValidation.Kind.OK);
    }

    public static boolean isInjectionEnabledForNode(Supplier<Set<LabelAtom>> assignedLabels,
                                                    Set<String> disabledNodes,
                                                    Set<String> enabledNodes) {
        Set<String> labels =
            CollectionUtil.safeStream(assignedLabels.get())
                .map(LabelAtom::getName)
                .collect(Collectors.toSet());

        return isNotDisabled(labels, disabledNodes) && isEnabled(labels, enabledNodes);
    }

    private static boolean isNotDisabled(Set<String> labels, Set<String> disabledNodes) {
        if (disabledNodes == null || disabledNodes.isEmpty()) {
            return true;
        }
        return labels.stream().noneMatch(disabledNodes::contains);
    }

    private static boolean isEnabled(Set<String> labels, Set<String> enabledNodes) {
        if (enabledNodes == null || enabledNodes.isEmpty()) {
            return true;
        }
        return enabledNodes.stream().anyMatch(labels::contains);
    }
}
