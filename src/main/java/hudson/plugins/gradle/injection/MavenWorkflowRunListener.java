package hudson.plugins.gradle.injection;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

@Extension
public class MavenWorkflowRunListener extends RunListener<WorkflowRun> implements MavenInjectionAware {

    @Override
    public void onStarted(WorkflowRun workflowRun, TaskListener listener) {
        super.onInitialize(workflowRun);

        setPreparedMavenProperties(workflowRun, listener);
    }

}
