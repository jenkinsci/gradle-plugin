package hudson.plugins.gradle.injection;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import hudson.EnvVars;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Item;
import hudson.plugins.gradle.Messages;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import hudson.util.VersionNumber;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
    private String accessKeyCredentialId;
    private Integer shortLivedTokenExpiry;

    private String gradlePluginVersion;
    private String ccudPluginVersion;
    private String gradlePluginRepositoryUrl;
    private String gradlePluginRepositoryUsername;
    private Secret gradlePluginRepositoryPassword;
    private String gradlePluginRepositoryCredentialId;
    private ImmutableList<NodeLabelItem> gradleInjectionEnabledNodes;
    private ImmutableList<NodeLabelItem> gradleInjectionDisabledNodes;
    private Boolean gradleCaptureTaskInputFiles;

    private String mavenExtensionVersion;
    private String ccudExtensionVersion;
    private String mavenExtensionRepositoryUrl;
    private String mavenExtensionRepositoryCredentialId;
    private String mavenExtensionCustomCoordinates;
    private String ccudExtensionCustomCoordinates;
    private ImmutableList<NodeLabelItem> mavenInjectionEnabledNodes;
    private ImmutableList<NodeLabelItem> mavenInjectionDisabledNodes;
    private Boolean mavenCaptureGoalInputFiles;

    private String npmAgentVersion;
    private ImmutableList<NodeLabelItem> npmInjectionEnabledNodes;
    private ImmutableList<NodeLabelItem> npmInjectionDisabledNodes;

    private boolean enforceUrl;
    private boolean checkForBuildAgentErrors;

    // Legacy property that is not used anymore
    private transient String injectionVcsRepositoryPatterns;
    private VcsRepositoryFilter parsedVcsRepositoryFilter = VcsRepositoryFilter.EMPTY;
    // Legacy properties, kept for reading old configurations
    private transient boolean injectMavenExtension;
    private transient boolean injectCcudExtension;

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
    public String getAccessKeyCredentialId() {
        return accessKeyCredentialId;
    }

    @DataBoundSetter
    public void setAccessKeyCredentialId(String accessKeyCredentialId) {
        this.accessKeyCredentialId = Util.fixEmptyAndTrim(accessKeyCredentialId);
    }

    @CheckForNull
    public Integer getShortLivedTokenExpiry() {
        return shortLivedTokenExpiry;
    }

    @DataBoundSetter
    public void setShortLivedTokenExpiry(Integer shortLivedTokenExpiry) {
        this.shortLivedTokenExpiry = shortLivedTokenExpiry;
    }

    @CheckForNull
    public String getGradlePluginRepositoryCredentialId() {
        return gradlePluginRepositoryCredentialId;
    }

    @DataBoundSetter
    public void setGradlePluginRepositoryCredentialId(String gradlePluginRepositoryCredentialId) {
        this.gradlePluginRepositoryCredentialId = Util.fixEmptyAndTrim(gradlePluginRepositoryCredentialId);
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
        this.gradleInjectionEnabledNodes = safeImmutableListCopy(gradleInjectionEnabledNodes);
    }

    @CheckForNull
    public List<NodeLabelItem> getGradleInjectionDisabledNodes() {
        return gradleInjectionDisabledNodes;
    }

    @DataBoundSetter
    public void setGradleInjectionDisabledNodes(List<NodeLabelItem> gradleInjectionDisabledNodes) {
        this.gradleInjectionDisabledNodes = safeImmutableListCopy(gradleInjectionDisabledNodes);
    }

    public Boolean isGradleCaptureTaskInputFiles() {
        return gradleCaptureTaskInputFiles;
    }

    @DataBoundSetter
    public void setGradleCaptureTaskInputFiles(Boolean gradleCaptureTaskInputFiles) {
        this.gradleCaptureTaskInputFiles = gradleCaptureTaskInputFiles;
    }

    public String getMavenExtensionVersion() {
        return mavenExtensionVersion;
    }

    @DataBoundSetter
    public void setMavenExtensionVersion(String mavenExtensionVersion) {
        this.mavenExtensionVersion = Util.fixEmptyAndTrim(mavenExtensionVersion);
    }

    @CheckForNull
    public String getMavenExtensionCustomCoordinates() {
        return mavenExtensionCustomCoordinates;
    }

    @DataBoundSetter
    public void setMavenExtensionCustomCoordinates(String mavenExtensionCustomCoordinates) {
        this.mavenExtensionCustomCoordinates = Util.fixEmptyAndTrim(mavenExtensionCustomCoordinates);
    }

    @CheckForNull
    public String getCcudExtensionCustomCoordinates() {
        return ccudExtensionCustomCoordinates;
    }

    @DataBoundSetter
    public void setCcudExtensionCustomCoordinates(String ccudExtensionCustomCoordinates) {
        this.ccudExtensionCustomCoordinates = Util.fixEmptyAndTrim(ccudExtensionCustomCoordinates);
    }

    public String getCcudExtensionVersion() {
        return ccudExtensionVersion;
    }

    @DataBoundSetter
    public void setCcudExtensionVersion(String ccudExtensionVersion) {
        this.ccudExtensionVersion = Util.fixEmptyAndTrim(ccudExtensionVersion);
    }

    @CheckForNull
    public String getMavenExtensionRepositoryUrl() {
        return mavenExtensionRepositoryUrl;
    }

    @DataBoundSetter
    public void setMavenExtensionRepositoryUrl(String mavenExtensionRepositoryUrl) {
        this.mavenExtensionRepositoryUrl = Util.fixEmptyAndTrim(mavenExtensionRepositoryUrl);
    }

    @CheckForNull
    public String getMavenExtensionRepositoryCredentialId() {
        return mavenExtensionRepositoryCredentialId;
    }

    @DataBoundSetter
    public void setMavenExtensionRepositoryCredentialId(String mavenExtensionRepositoryCredentialId) {
        this.mavenExtensionRepositoryCredentialId = Util.fixEmptyAndTrim(mavenExtensionRepositoryCredentialId);
    }

    @CheckForNull
    public List<NodeLabelItem> getMavenInjectionEnabledNodes() {
        return mavenInjectionEnabledNodes;
    }

    @DataBoundSetter
    public void setMavenInjectionEnabledNodes(List<NodeLabelItem> mavenInjectionEnabledNodes) {
        this.mavenInjectionEnabledNodes = safeImmutableListCopy(mavenInjectionEnabledNodes);
    }

    @CheckForNull
    public List<NodeLabelItem> getMavenInjectionDisabledNodes() {
        return mavenInjectionDisabledNodes;
    }

    @DataBoundSetter
    public void setMavenInjectionDisabledNodes(List<NodeLabelItem> mavenInjectionDisabledNodes) {
        this.mavenInjectionDisabledNodes = safeImmutableListCopy(mavenInjectionDisabledNodes);
    }

    public Boolean isMavenCaptureGoalInputFiles() {
        return mavenCaptureGoalInputFiles;
    }

    @DataBoundSetter
    public void setMavenCaptureGoalInputFiles(Boolean mavenCaptureGoalInputFiles) {
        this.mavenCaptureGoalInputFiles = mavenCaptureGoalInputFiles;
    }

    public String getNpmAgentVersion() {
        return npmAgentVersion;
    }

    @DataBoundSetter
    public void setNpmAgentVersion(String npmAgentVersion) {
        this.npmAgentVersion = Util.fixEmptyAndTrim(npmAgentVersion);
    }

    @CheckForNull
    public List<NodeLabelItem> getNpmInjectionEnabledNodes() {
        return npmInjectionEnabledNodes;
    }

    @DataBoundSetter
    public void setNpmInjectionEnabledNodes(List<NodeLabelItem> npmInjectionEnabledNodes) {
        this.npmInjectionEnabledNodes = safeImmutableListCopy(npmInjectionEnabledNodes);
    }

    @CheckForNull
    public List<NodeLabelItem> getNpmInjectionDisabledNodes() {
        return npmInjectionDisabledNodes;
    }

    @DataBoundSetter
    public void setNpmInjectionDisabledNodes(List<NodeLabelItem> npmInjectionDisabledNodes) {
        this.npmInjectionDisabledNodes = safeImmutableListCopy(npmInjectionDisabledNodes);
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

    public boolean isCheckForBuildAgentErrors() {
        return checkForBuildAgentErrors;
    }

    @DataBoundSetter
    public void setCheckForBuildAgentErrors(boolean checkForBuildAgentErrors) {
        this.checkForBuildAgentErrors = checkForBuildAgentErrors;
    }

    public boolean isInjectCcudExtension() {
        return injectCcudExtension;
    }

    @DataBoundSetter
    public void setInjectCcudExtension(boolean injectCcudExtension) {
        this.injectCcudExtension = injectCcudExtension;
    }

    public boolean isInjectMavenExtension() {
        return injectMavenExtension;
    }

    @DataBoundSetter
    public void setInjectMavenExtension(boolean injectMavenExtension) {
        this.injectMavenExtension = injectMavenExtension;
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

        setNpmInjectionEnabledNodes(null);
        setNpmInjectionDisabledNodes(null);
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckServer(@QueryParameter String value) {
        if (doesNotHaveAdministerPermission()) {
            return FormValidation.error("Validating Server URL requires 'Administer' permission");
        }

        return checkRequiredUrl(value);
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckGradlePluginVersion(@QueryParameter String value) {
        if (doesNotHaveAdministerPermission()) {
            return FormValidation.error("Validating Gradle Plugin version requires 'Administer' permission");
        }

        return checkVersion(value);
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckCcudPluginVersion(@QueryParameter String value) {
        if (doesNotHaveAdministerPermission()) {
            return FormValidation.error("Validating CCUD Plugin version requires 'Administer' permission");
        }

        return checkVersion(value);
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckGradlePluginRepositoryUrl(@QueryParameter String value) {
        if (doesNotHaveAdministerPermission()) {
            return FormValidation.error("Validating Gradle Plugin repository URL requires 'Administer' permission");
        }

        return checkUrl(value);
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckAccessKeyCredentialId(@QueryParameter String value) {
        if (doesNotHaveAdministerPermission()) {
            return FormValidation.error("Validating access key credential ID requires 'Administer' permission");
        }

        String accessKeyId = Util.fixEmptyAndTrim(value);
        if (accessKeyId == null) {
            return FormValidation.ok();
        }

        List<StringCredentials> credentials = CredentialsProvider.lookupCredentialsInItem(StringCredentials.class, null, null);

        String accessKeyFromCredentialId = credentials.stream()
                .filter(it -> it.getId().equals(accessKeyId))
                .findFirst()
                .map(it -> it.getSecret().getPlainText())
                .orElse(null);

        return DevelocityAccessCredentials.isValid(accessKeyFromCredentialId)
                ? FormValidation.ok()
                : FormValidation.error(Messages.InjectionConfig_InvalidAccessKey());
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckShortLivedTokenExpiry(@QueryParameter String value) {
        if (doesNotHaveAdministerPermission()) {
            return FormValidation.error("Validating short-lived token expiry requires 'Administer' permission");
        }

        String shortLivedTokenExpiry = Util.fixEmptyAndTrim(value);
        if (shortLivedTokenExpiry == null) {
            return FormValidation.ok();
        }

        if (StringUtils.isNumeric(shortLivedTokenExpiry)) {
            int expiry = Integer.parseInt(shortLivedTokenExpiry);
            if (expiry > 0 && expiry <= 24) {
                return FormValidation.ok();
            }
        }

        return FormValidation.error(Messages.InjectionConfig_InvalidShortLivedTokenExpiry());
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckMavenExtensionCustomCoordinates(@QueryParameter String value) {
        if (doesNotHaveAdministerPermission()) {
            return FormValidation.error("Validating Maven Extension custom coordinates requires 'Administer' permission");
        }

        return validateMavenCoordinates(value);
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckCcudExtensionCustomCoordinates(@QueryParameter String value) {
        if (doesNotHaveAdministerPermission()) {
            return FormValidation.error("Validating Maven Extension custom coordinates requires 'Administer' permission");
        }

        return validateMavenCoordinates(value);
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckMavenExtensionRepositoryUrl(@QueryParameter String value) {
        if (doesNotHaveAdministerPermission()) {
            return FormValidation.error("Validating Maven Extension repository URL requires 'Administer' permission");
        }

        return checkUrl(value);
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckMavenExtensionVersion(@QueryParameter String value) {
        if (doesNotHaveAdministerPermission()) {
            return FormValidation.error("Validating Maven Extension version requires 'Administer' permission");
        }

        return checkVersion(value);
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckCcudExtensionVersion(@QueryParameter String value) {
        if (doesNotHaveAdministerPermission()) {
            return FormValidation.error("Validating CCUD Extension version requires 'Administer' permission");
        }

        return checkVersion(value);
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckNpmAgentVersion(@QueryParameter String value) {
        if (doesNotHaveAdministerPermission()) {
            return FormValidation.error("Validating npm agent version requires 'Administer' permission");
        }

        return checkVersion(value);
    }

    @SuppressWarnings("called by Jelly")
    @POST
    public ListBoxModel doFillGradlePluginRepositoryCredentialIdItems(@AncestorInPath Item project) {
        return getAllCredentials(project);
    }

    @SuppressWarnings("called by Jelly")
    @POST
    public ListBoxModel doFillMavenExtensionRepositoryCredentialIdItems(@AncestorInPath Item project) {
        return getAllCredentials(project);
    }

    @SuppressWarnings("called by Jelly")
    @POST
    public ListBoxModel doFillAccessKeyCredentialIdItems(@AncestorInPath Item project) {
        return getAllCredentials(project);
    }

    private static ListBoxModel getAllCredentials(Item project) {
        StandardListBoxModel listBoxModel = new StandardListBoxModel();

        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null && jenkins.hasPermission(Jenkins.ADMINISTER)) {
            listBoxModel
                    .includeEmptyValue()
                    // Add project scoped credentials:
                    .includeMatchingAs(ACL.SYSTEM, project, StandardCredentials.class, Collections.emptyList(),
                            CredentialsMatchers.anyOf(
                                    CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
                                    CredentialsMatchers.instanceOf(StringCredentials.class)
                            ))
                    .includeMatchingAs(ACL.SYSTEM, jenkins, StandardCredentials.class, Collections.emptyList(),
                            CredentialsMatchers.anyOf(
                                    CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
                                    CredentialsMatchers.instanceOf(StringCredentials.class)
                            )
                    );
        }

        return listBoxModel;
    }

    private static FormValidation validateMavenCoordinates(String value) {
        String coord = Util.fixEmptyAndTrim(value);
        return coord == null || MavenCoordinates.isValid(coord) ? FormValidation.ok() : FormValidation.error(Messages.InjectionConfig_InvalidMavenExtensionCustomCoordinates());
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

        return DevelocityVersionValidator.getInstance().isValid(version)
                ? FormValidation.ok()
                : FormValidation.error(Messages.InjectionConfig_InvalidVersion());
    }

    /**
     * Invoked by XStream when this object is read into memory.
     */
    @SuppressWarnings("unused")
    protected Object readResolve() throws IOException, FormException {
        if (injectionVcsRepositoryPatterns != null) {
            String filters = migrateLegacyRepositoryFilters(injectionVcsRepositoryPatterns);
            parsedVcsRepositoryFilter = VcsRepositoryFilter.of(filters);
        }
        if (accessKey != null && accessKeyCredentialId == null) {
            StringCredentials stringCredentials = new StringCredentialsImpl(
                    CredentialsScope.GLOBAL,
                    UUID.randomUUID().toString(),
                    "Migrated Develocity Access Key",
                    Secret.fromString(accessKey.getPlainText())
            );

            SystemCredentialsProvider.getInstance().getCredentials().add(stringCredentials);
            SystemCredentialsProvider.getInstance().save();

            setAccessKeyCredentialId(stringCredentials.getId());
            accessKey = null;

            save();
        }
        if (gradlePluginRepositoryUsername != null && gradlePluginRepositoryPassword != null && gradlePluginRepositoryCredentialId == null) {
            StandardUsernamePasswordCredentials standardUsernameCredentials = new UsernamePasswordCredentialsImpl(
                    CredentialsScope.GLOBAL,
                    UUID.randomUUID().toString(),
                    "Migrated Gradle Plugin Repository credentials",
                    gradlePluginRepositoryUsername,
                    gradlePluginRepositoryPassword.getPlainText()
            );

            SystemCredentialsProvider.getInstance().getCredentials().add(standardUsernameCredentials);
            SystemCredentialsProvider.getInstance().save();

            setGradlePluginRepositoryCredentialId(standardUsernameCredentials.getId());
            gradlePluginRepositoryUsername = null;
            gradlePluginRepositoryPassword = null;

            save();
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

    private static boolean doesNotHaveAdministerPermission() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();

        return jenkins == null || !jenkins.hasPermission(Jenkins.ADMINISTER);
    }

    private static <T extends Serializable> ImmutableList<T> safeImmutableListCopy(@Nullable List<T> list) {
        return list == null ? null : ImmutableList.copyOf(list);
    }
}
