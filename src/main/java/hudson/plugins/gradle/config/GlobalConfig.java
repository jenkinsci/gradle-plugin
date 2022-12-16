package hudson.plugins.gradle.config;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import hudson.EnvVars;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.plugins.gradle.Messages;
import hudson.plugins.gradle.injection.*;
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
import java.net.URI;
import java.util.List;
import java.util.Set;

// TODO: Consider splitting into two forms, one for Gradle, and one for Maven
@Extension
public class GlobalConfig extends GlobalConfiguration {

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

    private boolean enrichedSummaryEnabled;

    private boolean injectionEnabled;

    private int httpClientTimeoutInSeconds;
    private int httpClientMaxRetries;
    private int httpClientDelayBetweenRetriesInSeconds;

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

    private String buildScanServer;
    private Secret buildScanAccessKey;
    public GlobalConfig() {
        load();
    }

    public static GlobalConfig get() {
        return ExtensionList.lookupSingleton(GlobalConfig.class);
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

    public boolean isInjectionEnabled() {
        return injectionEnabled;
    }

    public boolean isDisabled() {
        return !isEnabled();
    }

    @DataBoundSetter
    public void setInjectionEnabled(boolean injectionEnabled) {
        this.injectionEnabled = injectionEnabled;
    }

    public boolean isEnrichedSummaryEnabled() {
        return enrichedSummaryEnabled;
    }

    @DataBoundSetter
    public void setEnrichedSummaryEnabled(boolean enrichedSummaryEnabled) {
        this.enrichedSummaryEnabled = enrichedSummaryEnabled;
    }

    public int getHttpClientTimeoutInSeconds() {
        return httpClientTimeoutInSeconds;
    }

    @DataBoundSetter
    public void setHttpClientTimeoutInSeconds(int httpClientTimeoutInSeconds) {
        this.httpClientTimeoutInSeconds = httpClientTimeoutInSeconds;
    }

    public int getHttpClientMaxRetries() {
        return httpClientMaxRetries;
    }

    @DataBoundSetter
    public void setHttpClientMaxRetries(int httpClientMaxRetries) {
        this.httpClientMaxRetries = httpClientMaxRetries;
    }

    public int getHttpClientDelayBetweenRetriesInSeconds() {
        return httpClientDelayBetweenRetriesInSeconds;
    }

    @DataBoundSetter
    public void setHttpClientDelayBetweenRetriesInSeconds(int httpClientDelayBetweenRetriesInSeonds) {
        this.httpClientDelayBetweenRetriesInSeconds = httpClientDelayBetweenRetriesInSeonds;
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

    public String getBuildScanServer() {
        return buildScanServer;
    }

    public URI getBuildScanServerUri() {
        return URI.create(buildScanServer);
    }

    @DataBoundSetter
    public void setBuildScanServer(String buildScanServerUrl) {
        if (Util.fixEmptyAndTrim(buildScanServerUrl) == null) {
            this.buildScanServer = null;
        } else {
            this.buildScanServer = buildScanServerUrl;
        }
    }

    public Secret getBuildScanAccessKey() {
        return buildScanAccessKey;
    }

    @DataBoundSetter
    public void setBuildScanAccessKey(Secret buildScanAccessKey) {
        if (Util.fixEmptyAndTrim(buildScanAccessKey.getPlainText()) == null) {
            this.buildScanAccessKey = null;
        } else {
            this.buildScanAccessKey = buildScanAccessKey;
        }
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
    public FormValidation doCheckHttpClientTimeoutInSeconds(@QueryParameter int value) {
        if(value >= 0 && value <= 300) {
            return FormValidation.ok();
        } else {
            return FormValidation.error("timeout must be in [0,300]");
        }
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckHttpClientMaxRetries(@QueryParameter int value) {
        if(value >= 0 && value <= 20) {
            return FormValidation.ok();
        } else {
            return FormValidation.error("max retries must be in [0,20]");
        }
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckHttpClientDelayBetweenRetriesInSeconds(@QueryParameter int value) {
        if(value >= 0 && value <= 20) {
            return FormValidation.ok();
        } else {
            return FormValidation.error("Delay between retries must be in [0,20]");
        }
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
}
