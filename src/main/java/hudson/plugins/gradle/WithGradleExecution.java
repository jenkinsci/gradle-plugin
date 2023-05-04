package hudson.plugins.gradle;

import hudson.model.Run;
import hudson.plugins.gradle.enriched.EnrichedSummaryConfig;
import hudson.plugins.gradle.enriched.ScanDetailService;
import hudson.plugins.gradle.util.RunUtil;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;

public class WithGradleExecution extends StepExecution {

    WithGradleExecution(StepContext context) {
        super(context);
    }

    @Override
    public boolean start() throws IOException, InterruptedException {
        GradleTaskListenerDecorator gradleTaskListenerDecorator = new GradleTaskListenerDecorator();

        getContext()
            .newBodyInvoker()
            .withContext(TaskListenerDecorator.merge(getContext().get(TaskListenerDecorator.class), gradleTaskListenerDecorator))
            .withCallback(new BuildScanCallback(gradleTaskListenerDecorator, getContext()))
            .start();

        return false;
    }

    private static class BuildScanCallback extends BodyExecutionCallback {

        private final BuildScansAware buildScans;
        private final StepContext parentContext;

        public BuildScanCallback(BuildScansAware buildScans, StepContext parentContext) {
            this.buildScans = buildScans;
            this.parentContext = parentContext;
        }

        @Override
        public void onSuccess(StepContext context, Object result) {
            parentContext.onSuccess(extractBuildScans(context));
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            parentContext.onFailure(t);
            extractBuildScans(context);
        }

        private List<String> extractBuildScans(StepContext context) {
            try {
                List<String> buildScans = this.buildScans.getBuildScans();
                if (buildScans.isEmpty()) {
                    return Collections.emptyList();
                }

                FlowNode flowNode = context.get(FlowNode.class);
                flowNode.getParents().stream().findFirst().ifPresent(parent -> {
                    BuildScanFlowAction nodeBuildScanAction = new BuildScanFlowAction(parent);
                    buildScans.forEach(nodeBuildScanAction::addScanUrl);
                    parent.addAction(nodeBuildScanAction);
                });

                ScanDetailService scanDetailService = new ScanDetailService(EnrichedSummaryConfig.get());

                Run run = context.get(Run.class);
                BuildScanAction buildScanAction = RunUtil.getOrCreateBuildScanAction(run);
                buildScans.forEach(scanUrl -> {
                    buildScanAction.addScanUrl(scanUrl);
                    scanDetailService.getScanDetail(scanUrl)
                        .ifPresent(buildScanAction::addScanDetail);
                });

                return buildScans;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }
}
