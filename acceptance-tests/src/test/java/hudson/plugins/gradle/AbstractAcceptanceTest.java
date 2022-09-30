package hudson.plugins.gradle;

import com.google.common.base.Preconditions;
import hudson.plugin.gradle.BuildScansInjectionSettings;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
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
import java.net.URI;
import java.nio.file.Files;
import java.util.function.Consumer;

public abstract class AbstractAcceptanceTest extends AbstractJUnitTest {

    private static final URI PUBLIC_GE_SERVER = URI.create("https://scans.gradle.com");

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

    protected final void enableBuildScansForGradle(URI server, String agentVersion) {
        updateBuildScansInjectionSettings(settings -> {
            settings.clickBuildScansInjection();
            settings.setGradleEnterpriseServerUrl(server);
            settings.setGradleEnterprisePluginVersion(agentVersion);
        });
    }

    protected final void enableBuildScansForMaven(String agentVersion) {
        updateBuildScansInjectionSettings(settings -> {
            settings.clickBuildScansInjection();
            settings.setGradleEnterpriseServerUrl(PUBLIC_GE_SERVER);
            settings.setGradleEnterpriseExtensionVersion(agentVersion);
        });
    }

    private void updateBuildScansInjectionSettings(Consumer<BuildScansInjectionSettings> spec) {
        BuildScansInjectionSettings settings = new BuildScansInjectionSettings(jenkins);
        settings.configure();

        spec.accept(settings);

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
}
