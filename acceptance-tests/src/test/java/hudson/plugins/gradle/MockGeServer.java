package hudson.plugins.gradle;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import org.junit.rules.ExternalResource;
import ratpack.handling.Context;
import ratpack.http.Status;
import ratpack.test.embed.EmbeddedApp;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public final class MockGeServer extends ExternalResource {

    private static final long TEN_MEGABYTES_IN_BYTES = 1024 * 1024 * 10;

    private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper(new JsonFactory());
    private static final ObjectWriter JSON_WRITER = JSON_OBJECT_MAPPER.writer();

    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE =
        new TypeReference<>() {
        };

    public static final String PUBLIC_BUILD_SCAN_ID = "z7o6hj5ag6bpc";
    public static final String DEFAULT_SCAN_UPLOAD_TOKEN = "scan-upload-token";

    private final List<ScanTokenRequest> scanTokenRequests = Collections.synchronizedList(new LinkedList<>());

    private boolean rejectRequests;
    private EmbeddedApp mockGeServer;

    @Override
    protected void before() {
        mockGeServer = EmbeddedApp.fromHandlers(c -> c
            .all(this::maybeRejectRequestHandler)
            .prefix("scans/publish", c1 -> c1
                .post("gradle/:pluginVersion/token", this::handleToken)
                .post("gradle/:pluginVersion/upload", this::handleUpload)
                .notFound()));
    }

    @Override
    protected void after() {
        mockGeServer.close();
    }

    private void handleToken(Context ctx) {
        ctx.getRequest().getBody(TEN_MEGABYTES_IN_BYTES).then(request -> {
            Map<String, Object> requestBody =
                JSON_OBJECT_MAPPER.readValue(request.getText(), MAP_TYPE_REFERENCE);

            scanTokenRequests.add(
                new ScanTokenRequest(
                    (String) requestBody.get("buildToolType"),
                    (String) requestBody.get("buildToolVersion"),
                    (String) requestBody.get("buildAgentVersion")
                ));

            Map<String, String> responseBody =
                ImmutableMap.of(
                    "id", PUBLIC_BUILD_SCAN_ID,
                    "scanUrl", publicBuildScanId(),
                    "scanUploadUrl", scanUploadUrl(ctx),
                    "scanUploadToken", DEFAULT_SCAN_UPLOAD_TOKEN
                );

            ctx.getResponse()
                .contentType("application/vnd.gradle.scan-ack+json")
                .send(JSON_WRITER.writeValueAsBytes(responseBody));
        });
    }

    private void maybeRejectRequestHandler(Context ctx) {
        if (rejectRequests) {
            ctx.getResponse().status(Status.BAD_GATEWAY).send();
        } else {
            ctx.next();
        }
    }

    private void handleUpload(Context ctx) {
        ctx.getRequest().getBody(TEN_MEGABYTES_IN_BYTES)
            .then(__ ->
                ctx.getResponse()
                    .contentType("application/vnd.gradle.scan-upload-ack+json")
                    .send("{}"));
    }

    private String scanUploadUrl(Context ctx) {
        String pluginVersion = ctx.getPathTokens().get("pluginVersion");
        return String.format("%sscans/publish/gradle/%s/upload", getAddress(), pluginVersion);
    }

    public String publicBuildScanId() {
        return String.format("%ss/%s", getAddress(), PUBLIC_BUILD_SCAN_ID);
    }

    public URI getAddress() {
        Preconditions.checkNotNull(mockGeServer, "mockGeServer has not yet been created");
        return mockGeServer.getAddress();
    }

    @Nullable
    public ScanTokenRequest getLastScanTokenRequest() {
        return Iterables.getLast(scanTokenRequests, null);
    }

    public void rejectRequests() {
        this.rejectRequests = true;
    }

    public static final class ScanTokenRequest {

        public final String toolType;
        public final String toolVersion;
        public final String agentVersion;

        private ScanTokenRequest(String toolType, String toolVersion, String agentVersion) {
            this.toolType = Preconditions.checkNotNull(toolType, "toolType must not be null");
            this.toolVersion = Preconditions.checkNotNull(toolVersion, "toolVersion must not be null");
            this.agentVersion = Preconditions.checkNotNull(agentVersion, "agentVersion must not be null");
        }
    }
}
