package hudson.plugins.gradle;

import org.jenkinsci.plugins.workflow.actions.FlowNodeAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class BuildScanFlowAction extends AbstractBuildScanAction implements FlowNodeAction {
    public BuildScanFlowAction(FlowNode target) {
        this.target = target;
    }

    @Override
    public void onLoad(FlowNode parent) {
        this.target = parent;
    }
}
