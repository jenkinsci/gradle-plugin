package hudson.plugins.gradle.injection;

import com.google.common.collect.ImmutableList;
import hudson.model.Actionable;
import hudson.model.Run;
import hudson.plugins.gradle.AbstractGradleLogProcessor;
import hudson.plugins.gradle.BuildAgentError;

import javax.annotation.Nullable;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class GradleEnterpriseExceptionLogProcessor extends AbstractGradleLogProcessor {

    private static final List<GradleEnterpriseExceptionDetector> DETECTORS =
        ImmutableList.of(new GradlePluginExceptionDetector(), new MavenExtensionExceptionDetector());

    private final BuildAgentErrorListener listener;

    public GradleEnterpriseExceptionLogProcessor(OutputStream out, @Nullable Run<?, ?> build) {
        this(out, build != null ? build.getCharset() : StandardCharsets.UTF_8, build);
    }

    public GradleEnterpriseExceptionLogProcessor(OutputStream out, Charset charset, Actionable actionable) {
        super(out, charset);
        this.listener = new DefaultBuildAgentErrorListener(actionable);
    }

    @Override
    public void processLogLine(String line) {
        for (GradleEnterpriseExceptionDetector detector : DETECTORS) {
            if (detector.detect(line)) {
                BuildAgentError buildAgentError = new BuildAgentError(detector.getBuildToolType());
                listener.onBuildAgentError(buildAgentError);
            }
        }
    }
}
