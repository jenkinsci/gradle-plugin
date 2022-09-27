package hudson.plugins.gradle.injection;

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

@Restricted(NoExternalUse.class)
@Extension
public class InjectionConfig extends GlobalConfiguration {

    private boolean enabled;

    private String server;
    private boolean allowUntrusted;
    private String accessKey;

    private String gradlePluginVersion;
    private String ccudPluginVersion;
    private String gradlePluginRepositoryUrl;

    private String mavenExtensionVersion;
    private String ccudExtensionVersion;

    // TODO: add labels

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

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) {
        req.bindJSON(this, json);
        save();
        return true;
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
    public FormValidation doCheckMavenExtensionVersion(@QueryParameter String value) {
        return checkVersion(value);
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckCcudExtensionVersion(@QueryParameter String value) {
        return checkVersion(value);
    }

    private static FormValidation checkRequiredUrl(String value) {
        return checkUrl(value, true);
    }

    private static FormValidation checkUrl(String value) {
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

    private static FormValidation checkVersion(String value) {
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
