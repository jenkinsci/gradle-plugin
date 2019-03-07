package hudson.plugins.gradle;

import hudson.console.LineTransformationOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author ikikko
 * @see <a href="https://github.com/jenkinsci/ant-plugin/blob/master/src/main/java/hudson/tasks/_ant/AntConsoleAnnotator.java">AntConsoleAnnotator</a>
 */
public class GradleConsoleAnnotator extends LineTransformationOutputStream {

    private static final Pattern BUILD_SCAN_PATTERN = Pattern.compile("Publishing (build scan|build information)\\.\\.\\.");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S*");

    private final OutputStream out;
    private final Charset charset;
    private final boolean usesGradleBuilder;
    private int linesSinceBuildScanPublishingMessage = Integer.MAX_VALUE;
    private List<BuildScanPublishedListener> buildScanListeners = new ArrayList<>();

    public GradleConsoleAnnotator(OutputStream out, Charset charset, boolean usesGradleBuilder) {
        this.out = out;
        this.charset = charset;
        this.usesGradleBuilder = usesGradleBuilder;
    }

    @Override
    protected void eol(byte[] b, int len) throws IOException {
        if (len < 500) { // Don't parse too long lines
            String line = charset.decode(ByteBuffer.wrap(b, 0, len)).toString();

            // trim off CR/LF from the end
            line = trimEOL(line);

            if (usesGradleBuilder) {
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
                    for (BuildScanPublishedListener listener : buildScanListeners) {
                        listener.onBuildScanPublished(buildScanUrl);
                    }
                }
            }
            if (len < 70 && BUILD_SCAN_PATTERN.matcher(line).find()) {
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

    public void addBuildScanPublishedListener(BuildScanPublishedListener listener) {
        this.buildScanListeners.add(listener);
    }
}
