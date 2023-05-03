package hudson.plugins.gradle.injection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import hudson.EnvVars;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.plugins.gradle.Messages;
import hudson.util.FormValidation;
import hudson.util.Secret;
import hudson.util.VersionNumber;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.CheckForNull;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// TODO: Consider splitting into two forms, one for Gradle, and one for Maven
@Extension
public class InjectionConfig extends GlobalConfiguration {

    private static final String GIT_PLUGIN_SHORT_NAME = "git";

    private static final Set<String> LEGACY_GLOBAL_ENVIRONMENT_VARIABLES =
        ImmutableSet.of(
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_INJECTION",
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL",
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER",
            "GRADLE_ENTERPRISE_ACCESS_KEY",
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION",
            "JENKINSGRADLEPLUGIN_CCUD_PLUGIN_VERSION",
            "JENKINSGRADLEPLUGIN_GRADLE_PLUGIN_REPOSITORY_URL",
            "JENKINSGRADLEPLUGIN_GRADLE_INJECTION_ENABLED_NODES",
            "JENKINSGRADLEPLUGIN_GRADLE_INJECTION_DISABLED_NODES",
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_EXTENSION_VERSION",
            "JENKINSGRADLEPLUGIN_CCUD_EXTENSION_VERSION",
            "JENKINSGRADLEPLUGIN_MAVEN_INJECTION_ENABLED_NODES",
            "JENKINSGRADLEPLUGIN_MAVEN_INJECTION_DISABLED_NODES"
        );

    private boolean enabled;

    private String server;
    private boolean allowUntrusted;
    private Secret accessKey;

    private String gradlePluginVersion;
    private String ccudPluginVersion;
    private String gradlePluginRepositoryUrl;
    private ImmutableList<NodeLabelItem> gradleInjectionEnabledNodes;
    private ImmutableList<NodeLabelItem> gradleInjectionDisabledNodes;

    private boolean injectMavenExtension;
    private boolean injectCcudExtension;
    private ImmutableList<NodeLabelItem> mavenInjectionEnabledNodes;
    private ImmutableList<NodeLabelItem> mavenInjectionDisabledNodes;

    private boolean enforceUrl;

    // Legacy property that is not used anymore
    private transient String injectionVcsRepositoryPatterns;
    private VcsRepositoryFilter parsedVcsRepositoryFilter = VcsRepositoryFilter.EMPTY;

    public InjectionConfig() {
        load();
    }

    public static InjectionConfig get() {
        return ExtensionList.lookupSingleton(InjectionConfig.class);
    }

    @Restricted(NoExternalUse.class)
    public boolean isShowLegacyConfigurationWarning() {
        EnvVars envVars = EnvUtil.globalEnvironment();
        return envVars != null && LEGACY_GLOBAL_ENVIRONMENT_VARIABLES.stream().anyMatch(envVars::containsKey);
    }

    @Restricted(NoExternalUse.class)
    @CheckForNull
    public UnsupportedMavenPluginWarningDetails getUnsupportedMavenPluginWarningDetails() {
        VersionNumber mavenPluginVersion = InjectionUtil.mavenPluginVersionNumber().orElse(null);

        return mavenPluginVersion == null || InjectionUtil.isSupportedMavenPluginVersion(mavenPluginVersion)
            ? null
            : new UnsupportedMavenPluginWarningDetails(mavenPluginVersion);
    }

    @Restricted(NoExternalUse.class)
    public boolean isGitPluginInstalled() {
        return InjectionUtil.maybeGetPlugin(GIT_PLUGIN_SHORT_NAME).isPresent();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDisabled() {
        return !isEnabled();
    }

    @DataBoundSetter
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @CheckForNull
    public String getServer() {
        return server;
    }

    @DataBoundSetter
    public void setServer(String server) {
        this.server = Util.fixEmptyAndTrim(server);
    }

    public boolean isAllowUntrusted() {
        return allowUntrusted;
    }

    @DataBoundSetter
    public void setAllowUntrusted(boolean allowUntrusted) {
        this.allowUntrusted = allowUntrusted;
    }

    @CheckForNull
    public Secret getAccessKey() {
        return accessKey;
    }

    @DataBoundSetter
    public void setAccessKey(Secret accessKey) {
        if (Util.fixEmptyAndTrim(accessKey.getPlainText()) == null) {
            this.accessKey = null;
        } else {
            this.accessKey = accessKey;
        }
    }

    @CheckForNull
    public String getGradlePluginVersion() {
        return gradlePluginVersion;
    }

    @DataBoundSetter
    public void setGradlePluginVersion(String gradlePluginVersion) {
        this.gradlePluginVersion = Util.fixEmptyAndTrim(gradlePluginVersion);
    }

    @CheckForNull
    public String getCcudPluginVersion() {
        return ccudPluginVersion;
    }

    @DataBoundSetter
    public void setCcudPluginVersion(String ccudPluginVersion) {
        this.ccudPluginVersion = Util.fixEmptyAndTrim(ccudPluginVersion);
    }

    @CheckForNull
    public String getGradlePluginRepositoryUrl() {
        return gradlePluginRepositoryUrl;
    }

    @DataBoundSetter
    public void setGradlePluginRepositoryUrl(String gradlePluginRepositoryUrl) {
        this.gradlePluginRepositoryUrl = Util.fixEmptyAndTrim(gradlePluginRepositoryUrl);
    }

    @CheckForNull
    public List<NodeLabelItem> getGradleInjectionEnabledNodes() {
        return gradleInjectionEnabledNodes;
    }

    @DataBoundSetter
    public void setGradleInjectionEnabledNodes(List<NodeLabelItem> gradleInjectionEnabledNodes) {
        this.gradleInjectionEnabledNodes =
            gradleInjectionEnabledNodes == null ? null : ImmutableList.copyOf(gradleInjectionEnabledNodes);
    }

    @CheckForNull
    public List<NodeLabelItem> getGradleInjectionDisabledNodes() {
        return gradleInjectionDisabledNodes;
    }

    @DataBoundSetter
    public void setGradleInjectionDisabledNodes(List<NodeLabelItem> gradleInjectionDisabledNodes) {
        this.gradleInjectionDisabledNodes =
            gradleInjectionDisabledNodes == null ? null : ImmutableList.copyOf(gradleInjectionDisabledNodes);
    }

    public boolean isInjectMavenExtension() {
        return injectMavenExtension;
    }

    @DataBoundSetter
    public void setInjectMavenExtension(boolean injectMavenExtension) {
        this.injectMavenExtension = injectMavenExtension;
    }

    public boolean isInjectCcudExtension() {
        return injectCcudExtension;
    }

    @DataBoundSetter
    public void setInjectCcudExtension(boolean injectCcudExtension) {
        this.injectCcudExtension = injectCcudExtension;
    }

    @CheckForNull
    public List<NodeLabelItem> getMavenInjectionEnabledNodes() {
        return mavenInjectionEnabledNodes;
    }

    @DataBoundSetter
    public void setMavenInjectionEnabledNodes(List<NodeLabelItem> mavenInjectionEnabledNodes) {
        this.mavenInjectionEnabledNodes =
            mavenInjectionEnabledNodes == null ? null : ImmutableList.copyOf(mavenInjectionEnabledNodes);
    }

    @CheckForNull
    public List<NodeLabelItem> getMavenInjectionDisabledNodes() {
        return mavenInjectionDisabledNodes;
    }

    @DataBoundSetter
    public void setMavenInjectionDisabledNodes(List<NodeLabelItem> mavenInjectionDisabledNodes) {
        this.mavenInjectionDisabledNodes =
            mavenInjectionDisabledNodes == null ? null : ImmutableList.copyOf(mavenInjectionDisabledNodes);
    }

    @DataBoundSetter
    public void setVcsRepositoryFilter(String vcsRepositoryFilter) {
        this.parsedVcsRepositoryFilter = VcsRepositoryFilter.of(vcsRepositoryFilter);
    }

    public boolean isEnforceUrl() {
        return enforceUrl;
    }

    @DataBoundSetter
    public void setEnforceUrl(boolean enforceUrl) {
        this.enforceUrl = enforceUrl;
    }

    /**
     * Required to display filter in the UI.
     */
    @Restricted(NoExternalUse.class)
    @CheckForNull
    public String getVcsRepositoryFilter() {
        return parsedVcsRepositoryFilter.getVcsRepositoryFilter();
    }

    public boolean hasRepositoryFilter() {
        return !parsedVcsRepositoryFilter.isEmpty();
    }

    public VcsRepositoryFilter.Result matchesRepositoryFilter(String repositoryUrl) {
        return parsedVcsRepositoryFilter.matches(repositoryUrl);
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) {
        clearRepeatableProperties();
        req.bindJSON(this, json);
        save();
        return true;
    }

    private void clearRepeatableProperties() {
        setGradleInjectionEnabledNodes(null);
        setGradleInjectionDisabledNodes(null);

        setMavenInjectionEnabledNodes(null);
        setMavenInjectionDisabledNodes(null);
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckServer(@QueryParameter String value) {
        return checkRequiredUrl(value);
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckGradlePluginVersion(@QueryParameter String value) {
        return checkVersion(value);
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckCcudPluginVersion(@QueryParameter String value) {
        return checkVersion(value);
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckGradlePluginRepositoryUrl(@QueryParameter String value) {
        return checkUrl(value);
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckAccessKey(@QueryParameter String value) {
        String accessKey = Util.fixEmptyAndTrim(value);
        if (accessKey == null) {
            return FormValidation.ok();
        }

        return GradleEnterpriseAccessKeyValidator.getInstance().isValid(accessKey)
            ? FormValidation.ok()
            : FormValidation.error(Messages.InjectionConfig_InvalidAccessKey());
    }

    public static FormValidation checkRequiredUrl(String value) {
        return checkUrl(value, true);
    }

    public static FormValidation checkUrl(String value) {
        return checkUrl(value, false);
    }

    private static FormValidation checkUrl(String value, boolean required) {
        String url = Util.fixEmptyAndTrim(value);
        if (url == null) {
            return required
                ? FormValidation.error(Messages.InjectionConfig_Required())
                : FormValidation.ok();
        }

        return HttpUrlValidator.getInstance().isValid(url)
            ? FormValidation.ok()
            : FormValidation.error(Messages.InjectionConfig_InvalidUrl());
    }

    public static FormValidation checkRequiredVersion(String value) {
        return checkVersion(value, true);
    }

    public static FormValidation checkVersion(String value) {
        return checkVersion(value, false);
    }

    private static FormValidation checkVersion(String value, boolean required) {
        String version = Util.fixEmptyAndTrim(value);
        if (version == null) {
            return required
                ? FormValidation.error(Messages.InjectionConfig_Required())
                : FormValidation.ok();
        }

        return GradleEnterpriseVersionValidator.getInstance().isValid(version)
            ? FormValidation.ok()
            : FormValidation.error(Messages.InjectionConfig_InvalidVersion());
    }

    /**
     * Invoked by XStream when this object is read into memory.
     */
    @SuppressWarnings("unused")
    protected Object readResolve() {
        if (injectionVcsRepositoryPatterns != null) {
            String filters = migrateLegacyRepositoryFilters(injectionVcsRepositoryPatterns);
            parsedVcsRepositoryFilter = VcsRepositoryFilter.of(filters);
        }
        return this;
    }

    private static String migrateLegacyRepositoryFilters(String injectionVcsRepositoryPatterns) {
        return Arrays.stream(injectionVcsRepositoryPatterns.split(","))
            .map(Util::fixEmptyAndTrim)
            .filter(Objects::nonNull)
            .map(p -> VcsRepositoryFilter.INCLUSION_QUALIFIER + p)
            .collect(Collectors.joining(VcsRepositoryFilter.SEPARATOR));
    }
}
