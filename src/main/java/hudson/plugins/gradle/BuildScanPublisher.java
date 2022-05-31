package hudson.plugins.gradle;

import hudson.Extension;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class BuildScanPublisher extends Step {
    @DataBoundConstructor
    public BuildScanPublisher() {
    }

    @Override
    public StepExecution start(StepContext context) {
        return new Execution(context);
    }

    static class Execution extends SynchronousNonBlockingStepExecution<List<String>> {
        private static final long serialVersionUID = 1L;

        protected Execution(@Nonnull StepContext context) {
            super(context);
        }

        @Override
        protected List<String> run() throws Exception {
            Run run = getContext().get(Run.class);
            BuildScanLogScanner scanner = new BuildScanLogScanner(new DefaultBuildScanPublishedListener(run));
            try (
                    BufferedReader logReader = new BufferedReader(run.getLogReader());
                    Stream<String> lines = logReader.lines()
            ) {
                lines.forEach(scanner::scanLine);
            }
            BuildScanAction action = run.getAction(BuildScanAction.class);
            if (action != null) {
                return action.getScanUrls();
            }
            return Collections.emptyList();
        }
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(Run.class);
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Find published build scans";
        }

        @Override
        public String getFunctionName() {
            return "findBuildScans";
        }
    }
}
