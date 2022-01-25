package hudson.plugins.gradle;

import java.io.Serializable;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.DataBoundSetter;

public abstract class BuildScanStep extends Step implements Serializable {
    private String buildScanLabel;

    @DataBoundSetter
    public void setBuildScanLabel(String buildScanLabel) {
        this.buildScanLabel = buildScanLabel;
    }

    public String getBuildScanLabel() {
        return buildScanLabel;
    }
}
