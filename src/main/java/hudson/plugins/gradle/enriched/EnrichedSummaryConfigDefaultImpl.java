package hudson.plugins.gradle.enriched;

import hudson.plugins.gradle.config.GlobalConfig;

public class EnrichedSummaryConfigDefaultImpl implements EnrichedSummaryConfig {

    @Override
    public boolean isEnabled() {
        return GlobalConfig.get().isEnrichedSummaryEnabled();
    }

}
