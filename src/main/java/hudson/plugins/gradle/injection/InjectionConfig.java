package hudson.plugins.gradle.injection;

import com.google.common.collect.ImmutableList;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.plugins.gradle.Messages;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.CheckForNull;
import java.util.List;

// TODO: Consider splitting into two forms, one for Gradle, and one for Maven
@Extension
public class InjectionConfig extends GlobalConfiguration {

    private boolean enabled;

    private String server;
    private boolean allowUntrusted;
    private String accessKey;

    private String gradlePluginVersion;
    private String ccudPluginVersion;
    private String gradlePluginRepositoryUrl;
    private ImmutableList<NodeLabelItem> gradleInjectionEnabledNodes;
    private ImmutableList<NodeLabelItem> gradleInjectionDisabledNodes;

    private String mavenExtensionVersion;
    private String ccudExtensionVersion;
    private ImmutableList<NodeLabelItem> mavenInjectionEnabledNodes;
    private ImmutableList<NodeLabelItem> mavenInjectionDisabledNodes;

    public InjectionConfig() {
        load();
    }

    public static InjectionConfig get() {
        return ExtensionList.lookupSingleton(InjectionConfig.class);
    }

    public boolean isEnabled() {
        return enabled;
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
    public String getAccessKey() {
        return accessKey;
    }

    @DataBoundSetter
    public void setAccessKey(String accessKey) {
        this.accessKey = Util.fixEmptyAndTrim(accessKey);
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

    @CheckForNull
    public String getMavenExtensionVersion() {
        return mavenExtensionVersion;
    }

    @DataBoundSetter
    public void setMavenExtensionVersion(String mavenExtensionVersion) {
        this.mavenExtensionVersion = Util.fixEmptyAndTrim(mavenExtensionVersion);
    }

    @CheckForNull
    public String getCcudExtensionVersion() {
        return ccudExtensionVersion;
    }

    @DataBoundSetter
    public void setCcudExtensionVersion(String ccudExtensionVersion) {
        this.ccudExtensionVersion = Util.fixEmptyAndTrim(ccudExtensionVersion);
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

    public static FormValidation checkRequiredUrl(String value) {
        return checkUrl(value, true);
    }

    public static FormValidation checkUrl(String value) {
        return checkUrl(value, false);
    }

    private static FormValidation checkUrl(String value, boolean required) {
        String url = Util.fixEmptyAndTrim(value);
        if (url == null) {
            if (required) {
                return FormValidation.error(Messages.InjectionConfig_Required());
            }
            return FormValidation.ok();
        }

        if (!HttpUrlValidator.getInstance().isValid(url)) {
            return FormValidation.error(Messages.InjectionConfig_InvalidUrl());
        }
        return FormValidation.ok();
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
            if (required) {
                return FormValidation.error(Messages.InjectionConfig_Required());
            }
            return FormValidation.ok();
        }

        if (!GradleEnterpriseVersionValidator.getInstance().isValid(version)) {
            return FormValidation.error(Messages.InjectionConfig_InvalidVersion());
        }
        return FormValidation.ok();
    }
}
