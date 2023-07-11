package hudson.plugins.gradle.injection;

import hudson.console.ConsoleNote;
import hudson.model.Run;
import hudson.plugins.gradle.AbstractGradleLogProcessor;
import hudson.plugins.gradle.BuildAgentError;
import hudson.plugins.gradle.BuildToolType;

import java.io.OutputStream;

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

    public GradleEnterpriseExceptionLogProcessor(OutputStream out, Run<?, ?> build) {
        super(out, build.getCharset());
        this.listener = new DefaultBuildAgentErrorListener(build);
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
