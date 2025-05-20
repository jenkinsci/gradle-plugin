package hudson.plugins.gradle;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

import static hudson.plugins.gradle.TimestampPrefixDetector.detectTimestampPrefix;
import static hudson.plugins.gradle.TimestampPrefixDetector.trimTimestampPrefix;

/**
 * @author ikikko
 * @see <a href="https://github.com/jenkinsci/ant-plugin/blob/master/src/main/java/hudson/tasks/_ant/AntConsoleAnnotator.java">AntConsoleAnnotator</a>
 */
public final class GradleConsoleAnnotator extends AbstractGradleLogProcessor {

    private final boolean annotateGradleOutput;
    private final BuildScanLogScanner buildScanLogScanner;

    private transient Integer timestampPrefix;

    public GradleConsoleAnnotator(OutputStream out,
                                  Charset charset,
                                  boolean annotateGradleOutput,
                                  BuildScanPublishedListener buildScanListener) {
        super(out, charset);
        this.annotateGradleOutput = annotateGradleOutput;
        this.buildScanLogScanner = new BuildScanLogScanner(buildScanListener);
    }

    @Override
    protected void processLogLine(String line) throws IOException {
        // TODO: do we need to trim EOL?
        line = trimEOL(line);

        if (annotateGradleOutput) {
            // assumption that the timestamp prefix will be present or not for all lines
            if (timestampPrefix == null) {
                timestampPrefix = detectTimestampPrefix(line);
            }
            line = trimTimestampPrefix(timestampPrefix, line);
            if (line.startsWith(":") || line.startsWith("> Task :")) {
                new GradleTaskNote().encodeTo(out);
            }

            if (line.startsWith("BUILD SUCCESSFUL") || line.startsWith("BUILD FAILED")) {
                new GradleOutcomeNote().encodeTo(out);
            }
        }

        buildScanLogScanner.scanLine(line);
    }
}
