package hudson.plugins.gradle.injection.token;


import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.plugins.gradle.injection.DevelocityAccessKey;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class ShortLivedTokenClient {

    private static final RequestBody EMPTY_BODY = RequestBody.create(new byte[]{});
    private final OkHttpClient httpClient;
    private static final Logger LOGGER = LoggerFactory.getLogger(ShortLivedTokenClient.class);

    public ShortLivedTokenClient() {
        httpClient = new OkHttpClient().newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public Optional<DevelocityAccessKey> get(String server, DevelocityAccessKey accessKey, @Nullable Integer expiry) {
        String url = normalize(server) + "api/auth/token";
        if (expiry != null) {
            url = url + "?expiresInHours=" + expiry;
        }

        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + accessKey.getKey())
            .addHeader("Content-Type", "application/json")
            .post(EMPTY_BODY)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 200 && response.body() != null) {
                return Optional.of(DevelocityAccessKey.of(accessKey.getHostname(), response.body().string()));
            } else if (response.body() == null) {
                LOGGER.warn("Develocity short lived token request failed {} with empty body", url);
                return Optional.empty();
            } else {
                LOGGER.warn("Develocity short lived token request failed {} with status code {}", url, response.code());
                return Optional.empty();
            }
        } catch (IOException e) {
            LOGGER.warn("Short lived token request failed {}", url, e);
            return Optional.empty();
        }
    }

    private String normalize(String server) {
        if (server.endsWith("/")) {
            return server;
        }
        return server + "/";
    }

}
