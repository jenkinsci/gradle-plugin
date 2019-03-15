package hudson.plugins.gradle;

import hudson.console.LineTransformationOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author ikikko
 * @see <a href="https://github.com/jenkinsci/ant-plugin/blob/master/src/main/java/hudson/tasks/_ant/AntConsoleAnnotator.java">AntConsoleAnnotator</a>
 */
public class GradleConsoleAnnotator extends LineTransformationOutputStream {

    private static final Pattern BUILD_SCAN_PATTERN = Pattern.compile("Publishing (build scan|build information)\\.\\.\\.");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S*");
    private static final int MAX_PUBLISHED_MESSAGE_LENGTH = 70;

    private final OutputStream out;
    private final Charset charset;
    private final boolean annotateGradleOutput;
    private final BuildScanPublishedListener buildScanListener;
    private final int maxLineLength;

    private int linesSinceBuildScanPublishingMessage = Integer.MAX_VALUE;

    public GradleConsoleAnnotator(OutputStream out, Charset charset, boolean annotateGradleOutput, BuildScanPublishedListener buildScanListener) {
        this.out = out;
        this.charset = charset;
        this.annotateGradleOutput = annotateGradleOutput;
        this.buildScanListener = buildScanListener;
        this.maxLineLength = annotateGradleOutput ? 500 : MAX_PUBLISHED_MESSAGE_LENGTH;
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

            if (linesSinceBuildScanPublishingMessage < 10) {
                linesSinceBuildScanPublishingMessage++;
                Matcher matcher = URL_PATTERN.matcher(line);
                if (matcher.find()) {
                    linesSinceBuildScanPublishingMessage = Integer.MAX_VALUE;
                    String buildScanUrl = matcher.group();
                    buildScanListener.onBuildScanPublished(buildScanUrl);
                }
            }
            if (len < MAX_PUBLISHED_MESSAGE_LENGTH && BUILD_SCAN_PATTERN.matcher(line).find()) {
                linesSinceBuildScanPublishingMessage = 0;
            }
        }

        out.write(b, 0, len);
    }

    @Override
    public void close() throws IOException {
        super.close();
        out.close();
    }
}
