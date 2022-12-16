package hudson.plugins.gradle.enriched;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.plugins.gradle.config.GlobalConfig;
import hudson.util.Secret;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;

public class ScanDetailServiceDefaultImpl implements ScanDetailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScanDetailServiceDefaultImpl.class);

    private enum BuildToolType {
        maven, gradle
    }

    private final static ObjectMapper MAPPER = new ObjectMapper();
    private final Secret buildScanAccessToken;

    public ScanDetailServiceDefaultImpl(Secret buildScanAccessToken) {
        this.buildScanAccessToken = buildScanAccessToken;
    }

    @Override
    public ScanDetail getScanDetail(String buildScanUrl) {
        if (GlobalConfig.get().isEnrichedSummaryEnabled()) {
            return doGetScanDetail(buildScanUrl);
        }

        return null;
    }

    private ScanDetail doGetScanDetail(String buildScanUrl) {
        int scanPathIndex = buildScanUrl.lastIndexOf("/s/");
        if (scanPathIndex != -1) {
            String baseApiUrl = buildScanUrl.substring(0, scanPathIndex);
            String scanId = buildScanUrl.substring(scanPathIndex + 3);

            //FIXME remove me
            baseApiUrl = baseApiUrl.replaceAll("localhost", "host.docker.internal");

            try (CloseableHttpClient httpclient = buildHttpClient()) {
                HttpGet httpGetApiBuilds = buildGetRequest(baseApiUrl + "/api/builds/" + scanId);

                String buildToolType = "";
                String buildToolVersion = null;
                try (CloseableHttpResponse responseApiBuilds = httpclient.execute(httpGetApiBuilds)) {
                    if (responseApiBuilds.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                        HttpEntity httpEntity = responseApiBuilds.getEntity();
                        if (httpEntity != null) {
                            String retSrc = EntityUtils.toString(httpEntity);
                            JsonNode result = MAPPER.readTree(retSrc);
                            buildToolType = result.get("buildToolType").asText();
                            buildToolVersion = result.get("buildToolVersion").asText();
                            EntityUtils.consume(httpEntity);
                        }
                    } else {
                        LOGGER.info(String.format("Unable to fetch build scan data [%s]", responseApiBuilds.getStatusLine().getStatusCode()));
                    }
                }

                ScanDetail scanDetail = null;
                try {
                    BuildToolType buildToolTypeAsEnum = Enum.valueOf(BuildToolType.class, buildToolType);
                    switch (buildToolTypeAsEnum) {
                        case gradle:
                            HttpGet httpGetGradleAttributes = buildGetRequest(baseApiUrl + "/api/builds/" + scanId + "/gradle-attributes");

                            try (CloseableHttpResponse responseApiBuilds = httpclient.execute(httpGetGradleAttributes)) {
                                if (responseApiBuilds.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                                    HttpEntity httpEntity = responseApiBuilds.getEntity();
                                    if (httpEntity != null) {
                                        String retSrc = EntityUtils.toString(httpEntity);
                                        JsonNode result = MAPPER.readTree(retSrc);
                                        scanDetail = new ScanDetail.ScanDetailBuilder()
                                                .withProjectName(result.get("rootProjectName").asText())
                                                .withBuildToolType(buildToolType)
                                                .withBuildToolVersion(buildToolVersion)
                                                .withRequestedTasks(joinStringList(result.get("requestedTasks").elements()))
                                                .withHasFailed(result.get("hasFailed").asText())
                                                .withUrl(buildScanUrl)
                                                .build();
                                        EntityUtils.consume(httpEntity);
                                    }
                                } else {
                                    LOGGER.info(String.format("Unable to fetch build scan data [%s]", responseApiBuilds.getStatusLine().getStatusCode()));
                                }
                            }
                            break;
                        case maven:
                            HttpGet httpGetMavenAttributes = buildGetRequest(baseApiUrl + "/api/builds/" + scanId + "/maven-attributes");

                            try (CloseableHttpResponse responseApiBuilds = httpclient.execute(httpGetMavenAttributes)) {
                                if (responseApiBuilds.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                                    HttpEntity httpEntity = responseApiBuilds.getEntity();
                                    if (httpEntity != null) {
                                        String retSrc = EntityUtils.toString(httpEntity);
                                        JsonNode result = MAPPER.readTree(retSrc);
                                        scanDetail = new ScanDetail.ScanDetailBuilder()
                                                .withProjectName(result.get("topLevelProjectName").asText())
                                                .withBuildToolType(buildToolType)
                                                .withBuildToolVersion(buildToolVersion)
                                                .withRequestedTasks(joinStringList(result.get("requestedGoals").elements()))
                                                .withHasFailed(result.get("hasFailed").asText())
                                                .withUrl(buildScanUrl)
                                                .build();
                                        EntityUtils.consume(httpEntity);
                                    }
                                } else {
                                    LOGGER.info(String.format("Unable to fetch build scan data [%s]", responseApiBuilds.getStatusLine().getStatusCode()));
                                }
                            }
                            break;
                    }
                } catch (IllegalArgumentException ignored) {
                }


                return scanDetail;
            } catch (IOException e) {
                LOGGER.info(String.format("Error fetching data [%s]", e.getMessage()));
            }
        }
        return null;
    }

    private CloseableHttpClient buildHttpClient() {
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

    private HttpGet buildGetRequest(String url) {
        HttpGet httpGet = new HttpGet(url);
        addBearerAuth(httpGet);
        return httpGet;
    }

    private void addBearerAuth(HttpGet httpGetApiBuilds) {
        if (buildScanAccessToken != null) {
            httpGetApiBuilds.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + buildScanAccessToken.getPlainText());
        }
    }

    private String joinStringList(Iterator<JsonNode> requestedTasks) {
        StringBuilder sb = new StringBuilder();
        while (requestedTasks.hasNext()) {
            sb.append(StringUtils.remove(requestedTasks.next().asText(), "\""));
            sb.append(", ");
        }

        String result = sb.toString();
        return StringUtils.removeEnd(result, ", ");
    }
}
