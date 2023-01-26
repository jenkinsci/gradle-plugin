package hudson.plugins.gradle.enriched;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import hudson.util.Secret;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Optional;

public class ScanDetailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScanDetailService.class);

    private final static ObjectMapper MAPPER = new ObjectMapper();

    private static final String GRADLE_ENTERPRISE_PUBLIC_SERVER = "https://gradle.com";
    private static final String URL_CONTEXT_PATH_SCAN_ID = "/s/";
    private static final String URL_CONTEXT_PATH_API_BUILDS = "/api/builds/";
    private static final String URL_CONTEXT_PATH_GRADLE_ATTRIBUTES = "/gradle-attributes";
    private static final String URL_CONTEXT_PATH_MAVEN_ATTRIBUTES = "/maven-attributes";

    private HttpClientFactory httpClientFactory;

    private final boolean isEnrichedSummaryEnabled;
    private final Secret buildScanAccessToken;
    private final String buildScanServer;
    private final int httpClientTimeoutInSeconds;
    private final int httpClientMaxRetries;
    private final int httpClientDelayBetweenRetriesInSeconds;

    void setHttpClientFactory(HttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
    }

    public ScanDetailService(EnrichedSummaryConfig config) {
        this.isEnrichedSummaryEnabled = config.isEnrichedSummaryEnabled();
        this.buildScanAccessToken = config.getBuildScanAccessKey();
        this.buildScanServer = config.getBuildScanServer();
        this.httpClientTimeoutInSeconds = config.getHttpClientTimeoutInSeconds();
        this.httpClientMaxRetries = config.getHttpClientMaxRetries();
        this.httpClientDelayBetweenRetriesInSeconds = config.getHttpClientDelayBetweenRetriesInSeconds();
        this.httpClientFactory = new HttpClientFactory();
    }

    public Optional<ScanDetail> getScanDetail(String buildScanUrl) {
        if (isEnrichedSummaryEnabled && buildScanUrl != null) {
            return Optional.ofNullable(doGetScanDetail(buildScanUrl));
        }

        return Optional.empty();
    }

    private ScanDetail doGetScanDetail(String buildScanUrl) {
        if (buildScanUrl.startsWith(GRADLE_ENTERPRISE_PUBLIC_SERVER)) {
            // API is not accessible on public server
            return null;
        }

        String baseApiUri = getBaseApiUri(buildScanUrl);
        if(null == baseApiUri || baseApiUri.isEmpty()) {
            LOGGER.info("Gradle Enterprise API URL can't be resolved");
            return null;
        }

        try (CloseableHttpClient httpclient = httpClientFactory.buildHttpClient(httpClientTimeoutInSeconds, httpClientMaxRetries, httpClientDelayBetweenRetriesInSeconds)) {
            HttpGet httpGetApiBuilds = buildGetRequest(baseApiUri);

            ScanDetail scanDetail = new ScanDetail(buildScanUrl);
            try (CloseableHttpResponse responseApiBuilds = httpclient.execute(httpGetApiBuilds)) {
                if (responseApiBuilds.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                    LOGGER.info("Unable to fetch build scan data [{}]", responseApiBuilds.getStatusLine().getStatusCode());
                    return null;
                }

                HttpEntity httpEntityApiBuilds = responseApiBuilds.getEntity();
                if (httpEntityApiBuilds != null) {
                    String apiBuildsResponse = EntityUtils.toString(httpEntityApiBuilds);
                    ObjectReader objectReader = MAPPER.readerForUpdating(scanDetail);
                    scanDetail = objectReader.readValue(apiBuildsResponse);
                    String suffix = getUrlSuffix(scanDetail.getBuildToolType());
                    HttpGet httpGetBuildAttributes = buildGetRequest(baseApiUri + suffix);

                    try (CloseableHttpResponse responseApiBuildAttributes = httpclient.execute(httpGetBuildAttributes)) {
                        if (responseApiBuildAttributes.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                            LOGGER.info("Unable to fetch build scan data [{}]", responseApiBuildAttributes.getStatusLine().getStatusCode());
                            return null;
                        }

                        HttpEntity httpEntityApiBuildAttributes = responseApiBuildAttributes.getEntity();
                        if (httpEntityApiBuildAttributes != null) {
                            String apiBuildAttributesResponse = EntityUtils.toString(httpEntityApiBuildAttributes);
                            scanDetail = objectReader.readValue(apiBuildAttributesResponse);
                            return scanDetail;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.info("Error fetching data [{}]", e.getMessage());
        }

        return null;
    }

    private String getBaseApiUri(String buildScanUrl) {
        int scanIdStartIndex = buildScanUrl.lastIndexOf(URL_CONTEXT_PATH_SCAN_ID);
        if (scanIdStartIndex < 0) {
            return null;
        }
        String scanId = buildScanUrl.substring(scanIdStartIndex + URL_CONTEXT_PATH_SCAN_ID.length());

        try {
            URI baseApiUri = buildScanServer != null ?
                    URI.create(buildScanServer)
                    : URI.create(buildScanUrl).resolve("/");

            return baseApiUri.resolve(URL_CONTEXT_PATH_API_BUILDS).resolve(scanId).toASCIIString();
        } catch(IllegalArgumentException e) {
            LOGGER.info("URL can't be parsed [{}]", e.getMessage());
            return null;
        }
    }

    private static String getUrlSuffix(ScanDetail.BuildToolType buildToolType) {
        String suffix = "";
        switch (buildToolType) {
            case GRADLE:
                suffix = URL_CONTEXT_PATH_GRADLE_ATTRIBUTES;
                break;
            case MAVEN:
                suffix = URL_CONTEXT_PATH_MAVEN_ATTRIBUTES;
                break;
        }
        return suffix;
    }

    private HttpGet buildGetRequest(String uri) {
        HttpGet httpGet = new HttpGet(uri);
        addBearerAuth(httpGet);
        return httpGet;
    }

    private void addBearerAuth(HttpGet httpGetApiBuilds) {
        if (buildScanAccessToken != null) {
            httpGetApiBuilds.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + buildScanAccessToken.getPlainText());
        }
    }

}
