package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.PluginWrapper;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.labels.LabelAtom;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.gradle.util.CollectionUtil;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.VersionNumber;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class InjectionUtil {

    private static final String MAVEN_PLUGIN_SHORT_NAME = "maven-plugin";

    public static final VersionNumber MINIMUM_SUPPORTED_MAVEN_PLUGIN_VERSION = new VersionNumber("3.20");

    public static final String JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK = "JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK";

    private InjectionUtil() {
    }

    public static boolean globalAutoInjectionCheckEnabled(EnvVars envVars) {
        return EnvUtil.getEnv(envVars, JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK) != null;
    }

    public static Optional<VersionNumber> mavenPluginVersionNumber() {
        return Optional.ofNullable(Jenkins.getInstanceOrNull())
            .map(Jenkins::getPluginManager)
            .map(pm -> pm.getPlugin(MAVEN_PLUGIN_SHORT_NAME))
            .map(PluginWrapper::getVersionNumber);
    }

    public static boolean isSupportedMavenPluginVersion(@Nullable VersionNumber mavenPluginVersion) {
        return mavenPluginVersion != null
            && !mavenPluginVersion.isOlderThan(MINIMUM_SUPPORTED_MAVEN_PLUGIN_VERSION);
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

    public static boolean isVcsRepositoryAllowed(Run<?, ?> run, InjectionConfig config) {
        if (config.getParsedInjectionVcsRepositoryPatterns() == null || config.getParsedInjectionVcsRepositoryPatterns().isEmpty()) {
            return true;
        }

        if (run instanceof WorkflowRun) {
            Collection<? extends SCM> scms = ((WorkflowRun) run).getParent().getSCMs();

            for (SCM scm : scms) {
                if (vcsRepositoryUrlMatches(config, scm)) {
                    return true;
                }
            }
        } else if (run instanceof AbstractBuild) {
            AbstractProject<?, ?> project = ((AbstractBuild<?, ?>) run).getProject();
            SCM scm = project.getScm();

            if (scm == null) {
                return true;
            }

            return vcsRepositoryUrlMatches(config, scm);
        }

        return false;
    }

    private static boolean vcsRepositoryUrlMatches(InjectionConfig config, SCM scm) {
        if (scm instanceof GitSCM) {
            List<UserRemoteConfig> userRemoteConfigs = ((GitSCM) scm).getUserRemoteConfigs();

            for (UserRemoteConfig userRemoteConfig : userRemoteConfigs) {
                for (String pattern : config.getParsedInjectionVcsRepositoryPatterns()) {
                    String url = userRemoteConfig.getUrl();
                    if (url != null && url.contains(pattern)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

}
