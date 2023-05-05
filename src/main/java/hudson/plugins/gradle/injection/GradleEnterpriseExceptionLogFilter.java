package hudson.plugins.gradle.injection;

import hudson.Extension;
import hudson.console.ConsoleLogFilter;
import hudson.console.LineTransformationOutputStream;
import hudson.model.Run;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

@Extension
public class GradleEnterpriseExceptionLogFilter extends ConsoleLogFilter implements Serializable {

    @Override
    public OutputStream decorateLogger(Run build, OutputStream logger) {
        // TODO: Enable conditionally

        return new LineTransformationOutputStream() {

            @Override
            protected void eol(byte[] bytes, int length) throws IOException {
//                Charset charset = build.getCharset();
//                String line = charset.decode(ByteBuffer.wrap(bytes, 0, length)).toString();

                // TODO: process the line

                logger.write(bytes, 0, length);
            }

            @Override
            public void flush() throws IOException {
                logger.flush();
            }

            @Override
            public void close() throws IOException {
                super.close();
                logger.close();
            }
        };
    }
}
