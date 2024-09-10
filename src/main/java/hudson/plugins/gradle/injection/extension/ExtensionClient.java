package hudson.plugins.gradle.injection.extension;

import hudson.plugins.gradle.injection.MavenExtension;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.io.IOUtils;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

public final class ExtensionClient {

    public static final ExtensionClient INSTANCE = new ExtensionClient();

    private final OkHttpClient httpClient;

    private ExtensionClient() {
        this.httpClient = new OkHttpClient().newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public void downloadExtension(
            String downloadUrl,
            @Nullable MavenExtension.RepositoryCredentials repositoryCredentials,
            OutputStream outputStream
    ) throws IOException {
        Request.Builder requestBuilder = new Request.Builder().url(downloadUrl);
        if (repositoryCredentials != null) {
            String basicCredentials = Credentials.basic(repositoryCredentials.username(), repositoryCredentials.password());
            requestBuilder.addHeader("Authorization", basicCredentials);
        }

        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            ResponseBody responseBody = response.body();
            if (!response.isSuccessful() || responseBody == null) {
                throw new IOException("Could not download the extension from " + downloadUrl);
            }
            IOUtils.copy(responseBody.byteStream(), outputStream);
        }
    }

}
