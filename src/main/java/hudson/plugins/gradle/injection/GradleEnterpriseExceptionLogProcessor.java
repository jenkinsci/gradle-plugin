package hudson.plugins.gradle.injection;

import com.google.common.collect.ImmutableList;
import hudson.model.Actionable;
import hudson.model.Run;
import hudson.plugins.gradle.AbstractGradleLogProcessor;
import hudson.plugins.gradle.BuildAgentError;
import hudson.plugins.gradle.BuildScanAction;
import hudson.plugins.gradle.BuildToolType;
import hudson.plugins.gradle.util.RunUtil;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.function.Predicate;

public final class GradleEnterpriseExceptionLogProcessor extends AbstractGradleLogProcessor {

    private static final List<ExceptionDetector> DETECTORS =
        ImmutableList.of(
            new ExceptionDetector(BuildToolType.MAVEN, log -> log.startsWith("[ERROR] Internal error in Gradle Enterprise Maven extension:")),
            new ExceptionDetector(BuildToolType.GRADLE, log -> log.startsWith("Internal error in Gradle Enterprise Gradle plugin:"))
        );

    private final Actionable actionable;

    public GradleEnterpriseExceptionLogProcessor(OutputStream out, Run<?, ?> build) {
        this(out, build.getCharset(), build);
    }

    public GradleEnterpriseExceptionLogProcessor(OutputStream out, Charset charset, Actionable actionable) {
        super(out, charset);
        this.actionable = actionable;
    }

    @Override
    protected void processLogLine(String line) {
        for (ExceptionDetector detector : DETECTORS) {
            if (detector.test(line)) {
                BuildAgentError buildAgentError = new BuildAgentError(detector.buildToolType);
                RunUtil.getOrCreateAction(actionable, BuildScanAction.class, BuildScanAction::new)
                    .addBuildAgentError(buildAgentError);
            }
        }
    }

    private static class ExceptionDetector implements Predicate<String> {

        private final BuildToolType buildToolType;
        private final Predicate<String> predicate;

        ExceptionDetector(BuildToolType buildToolType, Predicate<String> predicate) {
            this.buildToolType = buildToolType;
            this.predicate = notEmpty(predicate);
        }

        @Override
        public boolean test(String line) {
            return predicate.test(line);
        }

        private static Predicate<String> notEmpty(Predicate<String> delegate) {
            return value -> {
                if (value == null || value.isEmpty()) {
                    return false;
                }
                return delegate.test(value);
            };
        }
    }
}
