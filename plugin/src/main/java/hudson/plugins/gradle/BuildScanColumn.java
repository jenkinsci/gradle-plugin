package hudson.plugins.gradle;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.visualization.table.FlowNodeViewColumn;
import org.jenkinsci.plugins.workflow.visualization.table.FlowNodeViewColumnDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class BuildScanColumn extends FlowNodeViewColumn {
    @DataBoundConstructor
    public BuildScanColumn() {}

    @Override
    public String getColumnCaption() {
        return ""; // no caption needed because icon is clear enough
    }

    @Extension
    public static class DescriptorImpl extends FlowNodeViewColumnDescriptor {
        @Override
        public FlowNodeViewColumn getDefaultInstance() {
            return new BuildScanColumn();
        }

        @Override
        public String getDisplayName() {
            return "Build Scans";
        }
    }
}
