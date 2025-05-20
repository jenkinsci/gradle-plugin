package hudson.plugins.gradle;

import hudson.console.LineTransformationOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public abstract class AbstractGradleLogProcessor extends LineTransformationOutputStream {

    // Don't parse too long lines
    private static final int DEFAULT_MAX_LINE_LENGTH = 500;

    private final int maxLineLength;
    protected final OutputStream out;
    protected final Charset charset;

    protected AbstractGradleLogProcessor(OutputStream out, Charset charset) {
        this(DEFAULT_MAX_LINE_LENGTH, out, charset);
    }

    protected AbstractGradleLogProcessor(int maxLineLength, OutputStream out, Charset charset) {
        this.maxLineLength = maxLineLength;
        this.out = out;
        this.charset = charset;
    }

    @Override
    protected final void eol(byte[] bytes, int length) throws IOException {
        if (length < maxLineLength) {
            String line = charset.decode(ByteBuffer.wrap(bytes, 0, length)).toString();
            processLogLine(line);
        }

        out.write(bytes, 0, length);
    }

    protected abstract void processLogLine(String line) throws IOException;

    @Override
    public final void flush() throws IOException {
        out.flush();
    }

    @Override
    public final void close() throws IOException {
        super.close();
        out.close();
    }
}
