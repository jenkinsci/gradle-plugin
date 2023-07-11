package hudson.plugins.gradle.injection;

import hudson.console.ConsoleNote;
import hudson.model.Actionable;
import hudson.model.Run;
import hudson.plugins.gradle.AbstractGradleLogProcessor;
import hudson.plugins.gradle.BuildAgentError;
import hudson.plugins.gradle.BuildToolType;

import javax.annotation.Nullable;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class GradleEnterpriseExceptionLogProcessor extends AbstractGradleLogProcessor {

    private static final GradleEnterpriseExceptionDetector[] DETECTORS =
        {
            new GradleEnterpriseExceptionDetector.ByPrefix(
                BuildToolType.GRADLE,
                "Internal error in Gradle Enterprise Gradle plugin:"
            ),
            new GradleEnterpriseExceptionDetector.ByPrefix(
                BuildToolType.MAVEN,
                "[ERROR] Internal error in Gradle Enterprise Maven extension:"
            )
        };

    private final BuildAgentErrorListener listener;

    public GradleEnterpriseExceptionLogProcessor(OutputStream out, @Nullable Run<?, ?> build) {
        this(out, build != null ? build.getCharset() : StandardCharsets.UTF_8, build);
    }

    public GradleEnterpriseExceptionLogProcessor(OutputStream out, Charset charset, Actionable actionable) {
        super(out, charset);
        this.listener = new DefaultBuildAgentErrorListener(actionable);
    }

    @Override
    protected void processLogLine(String line) {
        line = ConsoleNote.removeNotes(line);
        for (GradleEnterpriseExceptionDetector detector : DETECTORS) {
            if (detector.detect(line)) {
                BuildAgentError buildAgentError = new BuildAgentError(detector.getBuildToolType());
                listener.onBuildAgentError(buildAgentError);
            }
        }
    }
}
