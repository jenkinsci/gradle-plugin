package hudson.plugins.gradle;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import hudson.plugin.gradle.EnvironmentVariablesSettings;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.jenkinsci.test.acceptance.controller.JenkinsController;
import org.jenkinsci.test.acceptance.controller.LocalController;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.junit.After;
import org.junit.Before;
import ratpack.handling.Context;
import ratpack.test.embed.EmbeddedApp;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public abstract class AbstractAcceptanceTest extends AbstractJUnitTest {

    public static final String PUBLIC_BUILD_SCAN_ID = "z7o6hj5ag6bpc";
    public static final String DEFAULT_SCAN_UPLOAD_TOKEN = "scan-upload-token";

    @Inject
    public JenkinsController jenkinsController;

    private EmbeddedApp mockGeServer;

    @Before
    public void beforeEach() throws IOException {
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
    public void afterEach() {
        mockGeServer.close();
    }

    private EmbeddedApp createMockGeServer() {
        return EmbeddedApp.fromHandlers(c -> {

            ObjectWriter jsonWriter = new ObjectMapper(new JsonFactory()).writer();
            ObjectWriter smileWriter = new ObjectMapper(new SmileFactory()).writer();

            c
                .post("in/:gradleVersion/:pluginVersion", ctx -> {

                    System.out.println("Called: " + ctx.getRequest().getUri());

                    Map<String, String> body =
                        ImmutableMap.of(
                            "id", PUBLIC_BUILD_SCAN_ID,
                            "scanUrl", publicBuildScanId()
                        );

                    ByteArrayOutputStream bout = new ByteArrayOutputStream();
                    try (GZIPOutputStream zout = new GZIPOutputStream(bout)) {
                        smileWriter.writeValue(zout, body);
                    }

                    ctx.getResponse()
                        .contentType("application/vnd.gradle.scan-ack")
                        .send(bout.toByteArray());
                })
                .prefix("scans/publish", c1 -> {
                    c1
                        .post("gradle/:pluginVersion/token", ctx -> {

                            System.out.println("Called: " + ctx.getRequest().getUri());

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

                            System.out.println("Called: " + ctx.getRequest().getUri());

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

    private static boolean isEven(int number) {
        return (number & 1) == 0;
    }
}
