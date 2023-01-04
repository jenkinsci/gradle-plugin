package hudson.plugins.gradle.enriched;

import hudson.plugins.gradle.config.GlobalConfig;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class HttpClientProviderDefaultImpl implements HttpClientProvider {

    @Override
    public CloseableHttpClient buildHttpClient() {
        int httpClientTimeoutInSeconds = GlobalConfig.get().getHttpClientTimeoutInSeconds();
        int httpClientMaxRetries = GlobalConfig.get().getHttpClientMaxRetries();
        int httpClientDelayBetweenRetriesInSeconds = GlobalConfig.get().getHttpClientDelayBetweenRetriesInSeconds();

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
                        try {
                            // Sleep before retrying
                            Thread.sleep(httpClientDelayBetweenRetriesInSeconds * 1000L);
                        } catch (InterruptedException ignore) {
                        }

                        return true;
                    }
                }).build();
    }
}
