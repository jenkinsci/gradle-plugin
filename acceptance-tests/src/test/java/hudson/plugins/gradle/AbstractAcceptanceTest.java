package hudson.plugins.gradle;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import hudson.plugin.gradle.EnvironmentVariablesSettings;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SystemUtils;
import org.codehaus.plexus.util.Base64;
import org.jenkinsci.test.acceptance.controller.JenkinsController;
import org.jenkinsci.test.acceptance.controller.LocalController;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.Resource;
import org.jenkinsci.test.acceptance.slave.SlaveController;
import org.junit.Before;
import org.junit.Rule;
import org.zeroturnaround.zip.ZipUtil;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public abstract class AbstractAcceptanceTest extends AbstractJUnitTest {

    @Rule
    public MockGeServer mockGeServer = new MockGeServer();

    @Inject
    public JenkinsController jenkinsController;

    @Inject
    public SlaveController agentController;

    @Before
    public void commonBeforeEach() throws IOException {
        if (jenkinsController instanceof LocalController) {
            File jenkinsHome = ((LocalController) jenkinsController).getJenkinsHome();
            File updatesDirectory = new File(jenkinsHome, "updates");
            FileUtils.copyFileToDirectory(
                resource("/hudson.plugins.gradle.GradleInstaller").asFile(), updatesDirectory);
            FileUtils.copyFileToDirectory(
                resource("/hudson.tasks.Maven.MavenInstaller").asFile(), updatesDirectory);
        }
    }

    protected final void addGlobalEnvironmentVariables(String... variables) {
        Preconditions.checkArgument(
            isEven(ArrayUtils.getLength(variables)),
            "variables array must have an even length");

        EnvironmentVariablesSettings settings = new EnvironmentVariablesSettings(jenkins);
        settings.configure();
        settings.clickEnvironmentVariables();

        for (List<String> pair : Iterables.partition(Arrays.asList(variables), 2)) {
            settings.addEnvironmentVariable(pair.get(0), pair.get(1));
        }

        settings.save();
    }

    protected final String copyResourceDirStep(Resource dir) {
        Preconditions.checkState(SystemUtils.IS_OS_UNIX, "only UNIX is supported");

        File file = dir.asFile();
        Preconditions.checkArgument(file.isDirectory(), "'%s' is not a directory", dir.getName());

        File tmp = null;
        try {
            tmp = File.createTempFile("jenkins-acceptance-tests", "dir");
            ZipUtil.pack(file, tmp);
            byte[] archive = IOUtils.toByteArray(Files.newInputStream(tmp.toPath()));

            String shell = String.format(
                "base64 --decode << ENDOFFILE > archive.zip && unzip -o archive.zip \n%s\nENDOFFILE",
                new String(Base64.encodeBase64Chunked(archive)));

            return String.format("sh '''%s'''%n", shell);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (tmp != null) {
                tmp.delete();
            }
        }
    }

    private static boolean isEven(int number) {
        return (number & 1) == 0;
    }
}
