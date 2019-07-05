package hudson.plugins.gradle;

import hudson.console.LineTransformationOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * @author ikikko
 * @see <a href="https://github.com/jenkinsci/ant-plugin/blob/master/src/main/java/hudson/tasks/_ant/AntConsoleAnnotator.java">AntConsoleAnnotator</a>
 */
public class GradleConsoleAnnotator extends LineTransformationOutputStream {

    private final OutputStream out;
    private final Charset charset;
    private final boolean annotateGradleOutput;
    private final int maxLineLength;
    private final BuildScanLogScanner buildScanLogScanner;

    public GradleConsoleAnnotator(OutputStream out, Charset charset, boolean annotateGradleOutput, BuildScanPublishedListener buildScanListener) {
        this.out = out;
        this.charset = charset;
        this.annotateGradleOutput = annotateGradleOutput;
        this.maxLineLength = annotateGradleOutput ? 500 : BuildScanLogScanner.MAX_PUBLISHED_MESSAGE_LENGTH;
        this.buildScanLogScanner = new BuildScanLogScanner(buildScanListener);
    }

    @Override
    protected void eol(byte[] b, int len) throws IOException {
        if (len < maxLineLength) { // Don't parse too long lines
            String line = charset.decode(ByteBuffer.wrap(b, 0, len)).toString();

            // trim off CR/LF from the end
            line = trimEOL(line);

            if (annotateGradleOutput) {
                if (line.startsWith(":") || line.startsWith("> Task :"))
                // put the annotation
                {
                    new GradleTaskNote().encodeTo(out);
                }

                if (line.startsWith("BUILD SUCCESSFUL") || line.startsWith("BUILD FAILED")) {
                    new GradleOutcomeNote().encodeTo(out);
                }
            }

            buildScanLogScanner.scanLine(line);
        }

        out.write(b, 0, len);
    }

    @Override
    public void close() throws IOException {
        super.close();
        out.close();
    }
}
