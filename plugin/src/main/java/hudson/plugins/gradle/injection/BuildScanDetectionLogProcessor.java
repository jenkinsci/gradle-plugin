package hudson.plugins.gradle.injection;

import hudson.console.ConsoleNote;
import hudson.model.Run;
import hudson.plugins.gradle.AbstractGradleLogProcessor;
import hudson.plugins.gradle.BuildScanLogScanner;
import hudson.plugins.gradle.BuildScanPublishedListener;
import hudson.plugins.gradle.DefaultBuildScanPublishedListener;
import hudson.plugins.gradle.enriched.EnrichedSummaryConfig;
import hudson.plugins.gradle.enriched.ScanDetailService;

import javax.annotation.Nullable;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class BuildScanDetectionLogProcessor extends AbstractGradleLogProcessor {

    private final BuildScanLogScanner scanner;

    public BuildScanDetectionLogProcessor(OutputStream out, @Nullable Run<?, ?> build) {
        super(out, build != null ? build.getCharset() : StandardCharsets.UTF_8);
        ScanDetailService scanDetailService = new ScanDetailService(EnrichedSummaryConfig.get());
        DefaultBuildScanPublishedListener listener = new DefaultBuildScanPublishedListener(build, scanDetailService);
        this.scanner = new BuildScanLogScanner(listener);
    }

    BuildScanDetectionLogProcessor(OutputStream out, Charset charset, BuildScanPublishedListener listener) {
        super(out, charset);
        this.scanner = new BuildScanLogScanner(listener);
    }

    @Override
    protected void processLogLine(String line) {
        line = ConsoleNote.removeNotes(line);
        scanner.scanLine(line);
    }
}
