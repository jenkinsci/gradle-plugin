package hudson.plugins.gradle;

import com.google.common.collect.ImmutableSet;
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

    static class Execution extends SynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 1L;

        protected Execution(@Nonnull StepContext context) {
            super(context);
        }

        @Override
        protected Void run() throws Exception {
            Run run = getContext().get(Run.class);
            BuildScanLogScanner scanner = new BuildScanLogScanner(new DefaultBuildScanPublishedListener(run));
            try (
                    BufferedReader logReader = new BufferedReader(run.getLogReader());
                    Stream<String> lines = logReader.lines()
            ) {
                lines.forEach(scanner::scanLine);
            }
            return null;
        }
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class);
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
