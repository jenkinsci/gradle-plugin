package hudson.plugins.gradle;

import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public final class BuildAgentError {

    private final BuildToolType buildToolType;

    public BuildAgentError(BuildToolType buildToolType) {
        this.buildToolType = buildToolType;
    }

    public BuildToolType getBuildToolType() {
        return buildToolType;
    }
}
