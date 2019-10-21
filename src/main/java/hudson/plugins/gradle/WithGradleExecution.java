package hudson.plugins.gradle;

import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;

public class WithGradleExecution extends StepExecution {

    private GradleTaskListenerDecorator decorator;

    public WithGradleExecution(StepContext context, WithGradle withGradle) {
        super(context);
    }

    @Override
    public boolean start() throws IOException, InterruptedException {
        decorator = new GradleTaskListenerDecorator();
        getContext().newBodyInvoker()
                .withContext(TaskListenerDecorator.merge(getContext().get(TaskListenerDecorator.class), decorator))
                .withCallback(BodyExecutionCallback.wrap(getContext()))
                .withCallback(new BuildScanCallback()).start();

        return false;
    }

    @Override
    public void stop(@Nonnull Throwable cause) throws Exception {
    }


    private class BuildScanCallback extends BodyExecutionCallback {
        @Override
        public void onSuccess(StepContext context, Object result) {
            extractBuildScans(context, "Success");
        }

        private void extractBuildScans(StepContext context, final String listener) {
            try {
                PrintStream logger = context.get(TaskListener.class).getLogger();
                logger.println("Hello from on" + listener + "!");

                GradleTaskListenerDecorator newDecorator = decorator;
                logger.println("Found decorator: " + newDecorator);

                if (newDecorator == null) {
                    return;
                }
                context.onSuccess(newDecorator.getBuildScans());
                BuildScanAction buildScanAction = new BuildScanAction();
                newDecorator.getBuildScans().forEach(buildScanAction::addScanUrl);
                logger.println("Hello from on" + listener + "!");
                Run run = context.get(Run.class);
                logger.println("Got run :" + run);
                run.addAction(buildScanAction);
                logger.println("Done with on" + listener);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            extractBuildScans(context, "Failure");
        }
    }
}
