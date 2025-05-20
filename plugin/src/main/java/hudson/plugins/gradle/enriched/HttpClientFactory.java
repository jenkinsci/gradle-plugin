package hudson.plugins.gradle.enriched;

import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.util.concurrent.TimeUnit;

class HttpClientFactory {

    public CloseableHttpClient buildHttpClient(int httpClientTimeoutInSeconds, int httpClientMaxRetries, int httpClientDelayBetweenRetriesInSeconds) {
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(httpClientTimeoutInSeconds * 1000)
                .setConnectionRequestTimeout(httpClientTimeoutInSeconds * 1000)
                .setSocketTimeout(httpClientTimeoutInSeconds * 1000).build();

        return HttpClients.custom()
                .setDefaultRequestConfig(config)
                .setRetryHandler((exception, executionCount, context) -> {
                    if (executionCount > httpClientMaxRetries) {
                        return false;
                    } else {
                        Uninterruptibles.sleepUninterruptibly(httpClientDelayBetweenRetriesInSeconds, TimeUnit.SECONDS);
                        return true;
                    }
                }).build();
    }
}
