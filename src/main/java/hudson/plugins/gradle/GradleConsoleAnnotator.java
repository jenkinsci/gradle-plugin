package hudson.plugins.gradle;

import hudson.console.LineTransformationOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * @author ikikko
 */
public class GradleConsoleAnnotator extends LineTransformationOutputStream {
	private final OutputStream out;
	private final Charset charset;

	private boolean seenEmptyLine;

	public GradleConsoleAnnotator(OutputStream out, Charset charset) {
		this.out = out;
		this.charset = charset;
	}

	@Override
	protected void eol(byte[] b, int len) throws IOException {
		String line = charset.decode(ByteBuffer.wrap(b, 0, len)).toString();

		// trim off CR/LF from the end
		line = trimEOL(line);

		// TODO look again this condition
		// if (seenEmptyLine && endsWith(line, ':') && line.indexOf(' ') < 0)
		if (startsWith(line, ':'))
			// put the annotation
			new GradleTargetNote().encodeTo(out);

		if (line.equals("BUILD SUCCESSFUL") || line.equals("BUILD FAILED"))
			new GradleOutcomeNote().encodeTo(out);

		seenEmptyLine = line.length() == 0;
		out.write(b, 0, len);
	}

	private boolean startsWith(String line, char c) {
		int len = line.length();
		return len > 0 && line.charAt(0) == c;
	}

	@Override
	public void close() throws IOException {
		super.close();
		out.close();
	}

}
