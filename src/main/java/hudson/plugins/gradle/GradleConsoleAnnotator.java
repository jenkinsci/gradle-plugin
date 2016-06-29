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
    private boolean nextLineIsBuildScan;
    private String scanUrl;

    public GradleConsoleAnnotator(OutputStream out, Charset charset) {
        this.out = out;
        this.charset = charset;
    }

    @Override
    protected void eol(byte[] b, int len) throws IOException {
        String line = charset.decode(ByteBuffer.wrap(b, 0, len)).toString();

        // trim off CR/LF from the end
        line = trimEOL(line);

        if (line.startsWith(":"))
            // put the annotation
            new GradleTaskNote().encodeTo(out);

        if (line.equals("BUILD SUCCESSFUL") || line.equals("BUILD FAILED"))
            new GradleOutcomeNote().encodeTo(out);

        if (nextLineIsBuildScan) {
            scanUrl = line;
            nextLineIsBuildScan = false;
        }
        if (line.equals("Publishing build information...")) {
            nextLineIsBuildScan = true;
        }

        out.write(b, 0, len);
    }

    @Override
    public void close() throws IOException {
        super.close();
        out.close();
    }

    public String getScanUrl() {
        return scanUrl;
    }
}
