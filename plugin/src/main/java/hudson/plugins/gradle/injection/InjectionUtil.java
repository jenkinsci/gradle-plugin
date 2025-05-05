package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.PluginWrapper;
import hudson.model.labels.LabelAtom;
import hudson.plugins.gradle.util.CollectionUtil;
import hudson.util.FormValidation;
import hudson.util.VersionNumber;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import jenkins.model.Jenkins;

public final class InjectionUtil {

    private static final String MAVEN_PLUGIN_SHORT_NAME = "maven-plugin";

    public static final VersionNumber MINIMUM_SUPPORTED_MAVEN_PLUGIN_VERSION = new VersionNumber("3.20");

    public static final String JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK =
            "JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK";

    private InjectionUtil() {}

    public static boolean globalAutoInjectionCheckEnabled(EnvVars envVars) {
        return EnvUtil.getEnv(envVars, JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK) != null;
    }

    public static Optional<VersionNumber> mavenPluginVersionNumber() {
        return maybeGetPlugin(MAVEN_PLUGIN_SHORT_NAME).map(PluginWrapper::getVersionNumber);
    }

    public static Optional<PluginWrapper> maybeGetPlugin(String pluginShortName) {
        return Optional.ofNullable(Jenkins.getInstanceOrNull())
                .map(Jenkins::getPluginManager)
                .map(pm -> pm.getPlugin(pluginShortName));
    }

    public static boolean isSupportedMavenPluginVersion(@Nullable VersionNumber mavenPluginVersion) {
        return mavenPluginVersion != null && !mavenPluginVersion.isOlderThan(MINIMUM_SUPPORTED_MAVEN_PLUGIN_VERSION);
    }

    public static boolean isInvalid(FormValidation validation) {
        return !isValid(validation);
    }

    public static boolean isValid(FormValidation validation) {
        return validation.kind == FormValidation.Kind.OK;
    }

    public static boolean isAnyInvalid(FormValidation... validations) {
        return Arrays.stream(validations).anyMatch(InjectionUtil::isInvalid);
    }

    public static boolean isInjectionEnabledForNode(
            Supplier<Set<LabelAtom>> assignedLabels, Set<String> disabledNodes, Set<String> enabledNodes) {
        Set<String> labels = CollectionUtil.safeStream(assignedLabels.get())
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
