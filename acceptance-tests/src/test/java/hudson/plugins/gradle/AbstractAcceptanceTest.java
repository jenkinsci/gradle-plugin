package hudson.plugins.gradle;

import com.google.common.base.Preconditions;
import hudson.plugin.gradle.BuildScansInjectionSettings;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.text.StringSubstitutor;
import org.codehaus.plexus.util.Base64;
import org.jenkinsci.test.acceptance.controller.JenkinsController;
import org.jenkinsci.test.acceptance.controller.LocalController;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.Resource;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.slave.SlaveController;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.zeroturnaround.zip.ZipUtil;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@WithPlugins("gradle")
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

    protected final void enableEnrichedBuildScans() {
        enableEnrichedBuildScansWithServerOverride(null);
    }

    protected final void enableEnrichedBuildScansWithServerOverride(URI server) {
        updateBuildScansInjectionSettings(settings -> {
            settings.clickBuildScansEnriched();
            if (server != null) {
                settings.setGradleEnterpriseBuildScanServerUrl(server);
            }
        });
    }

    protected final void enableBuildScansForGradle(URI server, String agentVersion) {
        updateBuildScansInjectionSettings(settings -> {
            settings.clickBuildScansInjection();
            settings.setGradleEnterpriseServerUrl(server);
            settings.setGradleEnterprisePluginVersion(agentVersion);
            settings.setGradleEnterpriseAccessKey(String.format("%s=secret", server.getHost()));
        });
    }

    protected final void setGitRepositoryFilters(String filters) {
        updateBuildScansInjectionSettings(settings ->
            settings.setGitRepositoryFilters(filters)
        );
    }

    protected final String getGitRepositoryFilters() {
        BuildScansInjectionSettings settings = new BuildScansInjectionSettings(jenkins);
        settings.configure();

        return settings.getGitRepositoryFilters();
    }

    protected final void enableBuildScansForMaven() {
        updateBuildScansInjectionSettings(settings -> {
            settings.clickBuildScansInjection();
            settings.setGradleEnterpriseServerUrl(PUBLIC_GE_SERVER);
            settings.clickInjectMavenExtension();
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

    protected String resolveTemplate(String content, Map<String, String> tokens) {
        return new StringSubstitutor(tokens).replace(content);
    }

    protected Resource createTmpFile(TemporaryFolder tempFolder, String content) {
        try {
            File tmp = tempFolder.newFile();
            FileUtils.writeStringToFile(tmp, content, StandardCharsets.UTF_8);

            return new Resource(tmp.toURI().toURL());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static VcsFilterBuilder filter() {
        return new VcsFilterBuilder();
    }

    public static class VcsFilterBuilder {

        private final List<String> included = new ArrayList<>();
        private final List<String> excluded = new ArrayList<>();

        private VcsFilterBuilder() {
        }

        public VcsFilterBuilder include(String... filters) {
            included.addAll(Arrays.asList(filters));
            return this;
        }

        public VcsFilterBuilder exclude(String... filters) {
            excluded.addAll(Arrays.asList(filters));
            return this;
        }

        public String build() {
            return Stream.concat(
                    included.stream().map(f -> String.format("+:%s", f)),
                    excluded.stream().map(f -> String.format("-:%s", f)))
                .collect(Collectors.joining("\n"));
        }
    }
}
