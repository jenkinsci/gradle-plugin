package hudson.plugins.gradle;

import hudson.Extension;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.steps.Step;
import java.io.BufferedReader;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;

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
import hudson.Extension;
import hudson.model.Run;

public class BuildScanPublisher extends BuildScanStep {
    @DataBoundConstructor
    public BuildScanPublisher() {
    }

    @Override
    public StepExecution start(StepContext context) {
        return new Execution(context, this);
    }

    static class Execution extends SynchronousNonBlockingStepExecution<List<String>> {
        private static final long serialVersionUID = 1L;
        private final BuildScanPublisher buildScanPublisher;

        protected Execution(@Nonnull StepContext context, BuildScanPublisher buildScanPublisher) {
            super(context);
            this.buildScanPublisher = buildScanPublisher;
        }

        @Override
        protected List<String> run() throws Exception {
            Run run = getContext().get(Run.class);
            BuildScanLogScanner scanner = new BuildScanLogScanner(new DefaultBuildScanPublishedListener(run, buildScanPublisher.getBuildScanLabel()));
            try (
                    BufferedReader logReader = new BufferedReader(run.getLogReader());
                    Stream<String> lines = logReader.lines()
            ) {
                lines.forEach(scanner::scanLine);
            }
            BuildScanAction action = run.getAction(BuildScanAction.class);
            if (action != null) {
                return action.getBuildScans().stream().map(BuildScan::getUrl).collect(Collectors.toList());
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
