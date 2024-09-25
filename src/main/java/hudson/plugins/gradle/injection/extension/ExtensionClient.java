package hudson.plugins.gradle.injection.extension;

import hudson.plugins.gradle.injection.MavenExtension;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

public final class ExtensionClient {

    public static final ExtensionClient INSTANCE = new ExtensionClient();

    private static final String DEFAULT_REPOSITORY_URL = "https://repo1.maven.org/maven2/com/gradle/%s/%s/%s-%s.jar";

    private final OkHttpClient httpClient;

    private ExtensionClient() {
        this.httpClient = new OkHttpClient().newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public void downloadExtension(MavenExtension mavenExtension, String version, OutputStream outputStream) throws IOException {
        String url = String.format(DEFAULT_REPOSITORY_URL, mavenExtension.getName(), version, mavenExtension.getName(), version);

        Request request = new Request.Builder().url(url).build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            if (!response.isSuccessful() || responseBody == null) {
                throw new IOException("Could not download the extension from " + url);
            }
            IOUtils.copy(responseBody.byteStream(), outputStream);
        }
    }

}
