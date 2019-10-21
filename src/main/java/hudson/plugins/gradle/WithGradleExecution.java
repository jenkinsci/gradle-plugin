package hudson.plugins.gradle;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;

public class WithGradleExecution extends StepExecution {

    public WithGradleExecution(StepContext context, WithGradle withGradle) {
        super(context);
    }

    @Override
    public boolean start() throws IOException, InterruptedException {
        GradleTaskListenerDecorator decorator = new GradleTaskListenerDecorator();
        EnvVars envVars = getContext().get(EnvVars.class);
        String stage = envVars.get("STAGE_NAME");
        FlowNode flowNode = getContext().get(FlowNode.class);
        PrintStream logger = getContext().get(TaskListener.class).getLogger();
        Optional<String> nearestParallelStart = StreamSupport.stream(flowNode.iterateEnclosingBlocks().spliterator(), false)
                .map(node -> node.getAction(ThreadNameAction.class))
                .filter(Objects::nonNull)
                .map(ThreadNameAction::getThreadName)
                .findFirst();
        nearestParallelStart.ifPresent(branchName -> {
            logger.println("On branch: " + branchName);
        });

        getContext().newBodyInvoker()
                .withContext(TaskListenerDecorator.merge(getContext().get(TaskListenerDecorator.class), decorator))
                .withCallback(BodyExecutionCallback.wrap(getContext()))
                .withCallback(new BuildScanCallback(decorator, stage, nearestParallelStart.orElse(null))).start();

        return false;
    }

    @Override
    public void stop(@Nonnull Throwable cause) throws Exception {
    }


    private static class BuildScanCallback extends BodyExecutionCallback {
        private final GradleTaskListenerDecorator decorator;
        private final String stage;
        private final String parallelBranchName;

        public BuildScanCallback(GradleTaskListenerDecorator decorator, String stage, String parallelBranchName) {
            this.decorator = decorator;
            this.stage = stage;
            this.parallelBranchName = parallelBranchName;
        }

        @Override
        public void onSuccess(StepContext context, Object result) {
            extractBuildScans(context);
        }

        private void extractBuildScans(StepContext context) {
            try {
                PrintStream logger = context.get(TaskListener.class).getLogger();
                logger.println("Collecting build scans from stage " + stage);

                GradleTaskListenerDecorator newDecorator = decorator;
                logger.println("Found decorator: " + newDecorator);

                if (newDecorator == null) {
                    logger.println("WARNING: No decorator found, not looking for build scans");
                    return;
                }
                context.onSuccess(newDecorator.getBuildScans());
                Run run = context.get(Run.class);
                BuildScanAction existingAction = run.getAction(BuildScanAction.class);

                BuildScanAction buildScanAction = existingAction == null
                        ? new BuildScanAction()
                        : existingAction;
                newDecorator.getBuildScans().forEach(scanUrl -> buildScanAction.addScanUrl(stage, scanUrl, parallelBranchName));
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
