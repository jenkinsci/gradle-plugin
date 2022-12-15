package hudson.plugins.gradle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.model.Actionable;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.Iterator;

public class DefaultBuildScanPublishedListener implements BuildScanPublishedListener {
    private final Actionable target;

    private final static ObjectMapper MAPPER = new ObjectMapper();

    DefaultBuildScanPublishedListener(Actionable target) {
        this.target = target;
    }

    @Override
    public void onBuildScanPublished(String scanUrl) {
        BuildScanAction action = target.getAction(BuildScanAction.class);

        if (action == null) {
            action = new BuildScanAction();
            action.addScanUrl(scanUrl);

            getBuildScanDetails(scanUrl, action);

            target.addAction(action);
        } else {
            action.addScanUrl(scanUrl);

            getBuildScanDetails(scanUrl, action);
        }
    }

    private void getBuildScanDetails(String scanUrl, BuildScanAction action) {
        //FIXME HTTP
        // auth
        // create HTTP client once per job
        // set HTTP timeout
        // retry HTTP query
        // add HTTP delay to fetch data

        //FIXME configuration
        // feature toggle in configuration
        // configure retry + delay + timeout

        //FIXME UI
        // tab with padding
        // cut content and put in tooltip if too large

        //TODO make sure summaries with legacy mode are still fine


        int scanPathIndex = scanUrl.lastIndexOf("/s/");
        if(scanPathIndex != -1){
            String baseApiUrl = scanUrl.substring(0, scanPathIndex);
            String scanId = scanUrl.substring(scanPathIndex + 3);

            //FIXME remove me
            baseApiUrl = baseApiUrl.replaceAll("localhost", "host.docker.internal");

            try(CloseableHttpClient httpclient = HttpClients.createDefault()) {
                HttpGet httpGetApiBuilds = new HttpGet(baseApiUrl + "/api/builds/" + scanId);

                String buildToolType = null;
                String buildToolVersion = null;
                try(CloseableHttpResponse responseApiBuilds = httpclient.execute(httpGetApiBuilds)) {
                    if(responseApiBuilds.getStatusLine().getStatusCode() == HttpStatus.SC_OK){
                        HttpEntity httpEntity = responseApiBuilds.getEntity();
                        if (httpEntity != null) {
                            String retSrc = EntityUtils.toString(httpEntity);
                            JsonNode result = MAPPER.readTree(retSrc);
                            buildToolType = result.get("buildToolType").asText();
                            buildToolVersion = result.get("buildToolVersion").asText();
                            EntityUtils.consume(httpEntity);
                        }
                    }
                }

                ScanDetail scanDetail = null;
                if("gradle".equals(buildToolType)) {
                    HttpGet httpGetGradleAttributes = new HttpGet(baseApiUrl + "/api/builds/" + scanId + "/gradle-attributes");
                    try(CloseableHttpResponse responseApiBuilds = httpclient.execute(httpGetGradleAttributes)) {
                        if(responseApiBuilds.getStatusLine().getStatusCode() == HttpStatus.SC_OK){
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
                                        .withUrl(scanUrl)
                                        .build();
                                EntityUtils.consume(httpEntity);
                            }
                        }
                    }
                } else if("maven".equals(buildToolType)){
                    HttpGet httpGetGradleAttributes = new HttpGet(baseApiUrl + "/api/builds/" + scanId + "/maven-attributes");
                    try(CloseableHttpResponse responseApiBuilds = httpclient.execute(httpGetGradleAttributes)) {
                        if(responseApiBuilds.getStatusLine().getStatusCode() == HttpStatus.SC_OK){
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
                                        .withUrl(scanUrl)
                                        .build();
                                EntityUtils.consume(httpEntity);
                            }
                        }
                    }
                }
                action.addScanDetail(scanDetail);
            } catch (IOException e) {
                e.printStackTrace();
            }
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
