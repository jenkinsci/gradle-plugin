package hudson.plugins.gradle;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
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
import org.junit.After;
import org.junit.Before;
import org.zeroturnaround.zip.ZipUtil;
import ratpack.handling.Context;
import ratpack.test.embed.EmbeddedApp;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public abstract class AbstractAcceptanceTest extends AbstractJUnitTest {

    public static final String PUBLIC_BUILD_SCAN_ID = "z7o6hj5ag6bpc";
    public static final String DEFAULT_SCAN_UPLOAD_TOKEN = "scan-upload-token";

    @Inject
    public JenkinsController jenkinsController;

    private ObjectWriter jsonWriter = new ObjectMapper(new JsonFactory()).writer();

    private EmbeddedApp mockGeServer;

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

        mockGeServer = createMockGeServer();
    }

    @After
    public void commonAfterEach() {
        mockGeServer.close();
    }

    private EmbeddedApp createMockGeServer() {
        return EmbeddedApp.fromHandlers(c -> {
            c.prefix("scans/publish", a -> {
                a
                    .post("gradle/:pluginVersion/token", ctx -> {
                        Map<String, String> body =
                            ImmutableMap.of(
                                "id", PUBLIC_BUILD_SCAN_ID,
                                "scanUrl", publicBuildScanId(),
                                "scanUploadUrl", scanUploadUrl(ctx),
                                "scanUploadToken", DEFAULT_SCAN_UPLOAD_TOKEN
                            );

                        ctx.getResponse()
                            .contentType("application/vnd.gradle.scan-ack+json")
                            .send(jsonWriter.writeValueAsBytes(body));
                    })
                    .post("gradle/:pluginVersion/upload", ctx -> {
                        ctx.getRequest().getBody(1024 * 1024 * 10)
                            .then(__ ->
                                ctx.getResponse()
                                    .contentType("application/vnd.gradle.scan-upload-ack+json")
                                    .send());
                    })
                    .notFound();
            });
        });
    }

    private String scanUploadUrl(Context ctx) {
        String pluginVersion = ctx.getPathTokens().get("pluginVersion");
        return String.format("%sscans/publish/gradle/%s/upload", mockGeServerAddress(), pluginVersion);
    }

    protected final String publicBuildScanId() {
        return String.format("%ss/%s", mockGeServerAddress(), PUBLIC_BUILD_SCAN_ID);
    }

    protected final URI mockGeServerAddress() {
        Preconditions.checkNotNull(mockGeServer, "mockGeServer has not yet been created");
        return mockGeServer.getAddress();
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
