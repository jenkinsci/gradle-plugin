package hudson.plugins.gradle;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * @author ikikko
 * @see <a href="https://github.com/jenkinsci/ant-plugin/blob/master/src/main/java/hudson/tasks/_ant/AntConsoleAnnotator.java">AntConsoleAnnotator</a>
 */
public final class GradleConsoleAnnotator extends AbstractGradleLogProcessor {

    private final boolean annotateGradleOutput;
    private final BuildScanLogScanner buildScanLogScanner;

    public GradleConsoleAnnotator(OutputStream out,
                                  Charset charset,
                                  boolean annotateGradleOutput,
                                  BuildScanPublishedListener buildScanListener) {
        super(out, charset);
        this.annotateGradleOutput = annotateGradleOutput;
        this.buildScanLogScanner = new BuildScanLogScanner(buildScanListener);
    }

    @Override
    public void processLogLine(String line) throws IOException {
        // TODO: do we need to trim EOL?
        // trim off CR/LF from the end
        line = trimEOL(line);

        if (annotateGradleOutput) {
            if (line.startsWith(":") || line.startsWith("> Task :")) { // put the annotation
                new GradleTaskNote().encodeTo(out);
            }

            if (line.startsWith("BUILD SUCCESSFUL") || line.startsWith("BUILD FAILED")) {
                new GradleOutcomeNote().encodeTo(out);
            }
        }

        buildScanLogScanner.scanLine(line);
    }
}
