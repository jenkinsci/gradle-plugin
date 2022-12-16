package hudson.plugins.gradle;

import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.gradle.config.GlobalConfig;
import hudson.plugins.gradle.enriched.ScanDetail;
import hudson.plugins.gradle.enriched.ScanDetailServiceDefaultImpl;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.Collections;
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
                .withCallback(new BuildScanCallback(decorator, getContext())).start();

        return false;
    }

    private static class BuildScanCallback extends BodyExecutionCallback {
        private final GradleTaskListenerDecorator decorator;
        private final StepContext parentContext;

        public BuildScanCallback(GradleTaskListenerDecorator decorator, StepContext parentContext) {
            this.decorator = decorator;
            this.parentContext = parentContext;
        }

        @Override
        public void onSuccess(StepContext context, Object result) {
            parentContext.onSuccess(extractBuildScans(context));
        }

        private List<String> extractBuildScans(StepContext context) {
            try {
                PrintStream logger = context.get(TaskListener.class).getLogger();

                if (decorator == null) {
                    logger.println("WARNING: No decorator found, not looking for build scans");
                    return Collections.emptyList();
                }
                List<String> buildScans = decorator.getBuildScans();
                if (buildScans.isEmpty()) {
                    return Collections.emptyList();
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
                ScanDetailServiceDefaultImpl scanDetailService = new ScanDetailServiceDefaultImpl(GlobalConfig.get().getBuildScanAccessKey(), GlobalConfig.get().getBuildScanServerUri());
                buildScans.forEach(scanUrl -> {
                    buildScanAction.addScanUrl(scanUrl);
                    ScanDetail scanDetail = scanDetailService.getScanDetail(scanUrl);
                    buildScanAction.addScanDetail(scanDetail);
                });
                if (existingAction == null) {
                    run.addAction(buildScanAction);
                }
                return buildScans;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            parentContext.onFailure(t);
            extractBuildScans(context);
        }
    }
}
