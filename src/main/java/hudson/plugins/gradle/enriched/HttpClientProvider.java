package hudson.plugins.gradle.enriched;

import org.apache.http.impl.client.CloseableHttpClient;

public interface HttpClientProvider {

    CloseableHttpClient buildHttpClient();

}
