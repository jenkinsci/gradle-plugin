package hudson.plugins.gradle;

import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.List;

public class WithGradleExecution extends StepExecution {

    public WithGradleExecution(StepContext context, WithGradle withGradle) {
        super(context);
    }

    @Override
    public boolean start() throws IOException, InterruptedException {
        GradleTaskListenerDecorator decorator = new GradleTaskListenerDecorator();

        getContext().newBodyInvoker()
                .withContext(TaskListenerDecorator.merge(getContext().get(TaskListenerDecorator.class), decorator))
                .withCallback(BodyExecutionCallback.wrap(getContext()))
                .withCallback(new BuildScanCallback(decorator)).start();

        return false;
    }

    private static class BuildScanCallback extends BodyExecutionCallback {
        private final GradleTaskListenerDecorator decorator;

        public BuildScanCallback(GradleTaskListenerDecorator decorator) {
            this.decorator = decorator;
        }

        @Override
        public void onSuccess(StepContext context, Object result) {
            extractBuildScans(context);
        }

        private void extractBuildScans(StepContext context) {
            try {
                PrintStream logger = context.get(TaskListener.class).getLogger();

                if (decorator == null) {
                    logger.println("WARNING: No decorator found, not looking for build scans");
                    return;
                }
                List<String> buildScans = decorator.getBuildScans();
                context.onSuccess(buildScans);
                if (buildScans.isEmpty()) {
                    return;
                }
                Run run = context.get(Run.class);
                FlowNode flowNode = context.get(FlowNode.class);
                flowNode.getParents().stream().findFirst().ifPresent(parent -> {
                    BuildScanFlowAction nodeBuildScanAction = new BuildScanFlowAction(parent);
                    buildScans.forEach(nodeBuildScanAction::addScanUrl);
                    parent.addAction(nodeBuildScanAction);
                });

                BuildScanAction existingAction = run.getAction(BuildScanAction.class);
                BuildScanAction buildScanAction = existingAction == null
                        ? new BuildScanAction()
                        : existingAction;
                buildScans.forEach(buildScanAction::addScanUrl);
                if (existingAction == null) {
                    run.addAction(buildScanAction);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            extractBuildScans(context);
        }
    }
}
