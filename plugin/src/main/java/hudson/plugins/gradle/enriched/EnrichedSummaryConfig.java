package hudson.plugins.gradle.enriched;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

@Extension
public class EnrichedSummaryConfig extends GlobalConfiguration {
    private boolean enrichedSummaryEnabled;

    private int httpClientTimeoutInSeconds = 1;
    private int httpClientMaxRetries = 3;
    private int httpClientDelayBetweenRetriesInSeconds = 1;

    private String buildScanServer;
    private Secret buildScanAccessKey;

    public EnrichedSummaryConfig() {
        load();
    }

    public static EnrichedSummaryConfig get() {
        return ExtensionList.lookupSingleton(EnrichedSummaryConfig.class);
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

    public String getBuildScanServer() {
        return buildScanServer;
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
        req.bindJSON(this, json);
        save();
        return true;
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckHttpClientTimeoutInSeconds(@QueryParameter int value) {
        if (value >= 0 && value <= 300) {
            return FormValidation.ok();
        } else {
            return FormValidation.error("Timeout must be in [0,300].");
        }
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckHttpClientMaxRetries(@QueryParameter int value) {
        if (value >= 0 && value <= 20) {
            return FormValidation.ok();
        } else {
            return FormValidation.error("Max retries must be in [0,20].");
        }
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckHttpClientDelayBetweenRetriesInSeconds(@QueryParameter int value) {
        if (value >= 0 && value <= 20) {
            return FormValidation.ok();
        } else {
            return FormValidation.error("Delay between retries must be in [0,20].");
        }
    }
}
