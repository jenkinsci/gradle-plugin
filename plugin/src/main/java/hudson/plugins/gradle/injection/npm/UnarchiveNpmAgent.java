package hudson.plugins.gradle.injection.npm;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import hudson.util.IOUtils;
import jenkins.MasterToSlaveFileCallable;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;

/**
 * @see hudson.FilePath#untarFrom(InputStream, FilePath.TarCompression)
 */
public class UnarchiveNpmAgent extends MasterToSlaveFileCallable<Void> {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String PACKAGE_ENTRY_PREFIX = "package/";

    private final String version;
    private final InputStream in;

    public UnarchiveNpmAgent(String version, InputStream in) {
        this.version = version;
        this.in = in;
    }

    @Override
    public Void invoke(File dir, VirtualChannel channel) throws IOException {
        try (TarInputStream tar = new TarInputStream(FilePath.TarCompression.GZIP.extract(in), StandardCharsets.UTF_8.name())) {
            TarEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (entry.isFile()) {
                    File file = new File(dir, normalizeEntryName(entry));
                    File parent = file.getParentFile();
                    if (parent != null) {
                        IOUtils.mkdirs(parent);
                    }
                    IOUtils.copy(tar, file);

                    Files.setLastModifiedTime(file.toPath(), FileTime.from(entry.getModTime().toInstant()));
                    int mode = entry.getMode() & 0777;
                    if (mode != 0) {
                        new FilePath(file).chmod(mode); // noop on Windows
                    }
                }
            }
        } catch (IOException e) {
            throw wrapIntoIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw wrapIntoIOException(e);
        }
        return null;
    }

    private IOException wrapIntoIOException(Throwable cause) {
        return new IOException("Failed to extract the Develocity npm agent " + version, cause);
    }

    private static String normalizeEntryName(TarEntry entry) throws IOException {
        String entryName = entry.getName();
        if (!entryName.startsWith(PACKAGE_ENTRY_PREFIX)) {
            throw new IOException("Unexpected entry in the agent tarball: " + entryName);
        }
        return entryName.substring(PACKAGE_ENTRY_PREFIX.length());
    }
}
