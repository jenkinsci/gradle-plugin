package hudson.plugins.gradle.injection.download;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.concurrent.TimeUnit;

public final class AgentDownloadClient {

    private final OkHttpClient httpClient;

    public AgentDownloadClient() {
        httpClient = new OkHttpClient().newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public void download(URI downloadUrl, RequestAuthenticator authenticator, OutputStream outputStream) throws IOException {
        Request.Builder requestBuilder = new Request.Builder().url(downloadUrl.toURL());
        authenticator.authenticate(requestBuilder);

        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            ResponseBody responseBody = response.body();
            if (!response.isSuccessful() || responseBody == null) {
                throw new IOException("Could not download the agent from " + downloadUrl);
            }

            try (BufferedInputStream bufferedInputStream = new BufferedInputStream(responseBody.byteStream())) {
                bufferedInputStream.transferTo(outputStream);
            }
        }
    }
}
