package hudson.plugins.gradle;

import hudson.model.Run;
import jenkins.model.RunAction2;
import org.jenkinsci.plugins.workflow.actions.PersistentAction;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class BuildScanAction extends AbstractBuildScanAction implements PersistentAction, RunAction2 {

    @Override
    public void onAttached(Run<?, ?> r) {
        this.target = r;
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        this.target = r;
    }
}
