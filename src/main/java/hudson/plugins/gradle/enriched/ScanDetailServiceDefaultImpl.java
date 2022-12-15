package hudson.plugins.gradle.enriched;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.util.Secret;
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

public class ScanDetailServiceDefaultImpl implements ScanDetailService {

  private final static ObjectMapper MAPPER = new ObjectMapper();
  private final Secret buildScanAccessToken;

  public ScanDetailServiceDefaultImpl(Secret buildScanAccessToken) {
    this.buildScanAccessToken = buildScanAccessToken;
  }

  @Override
  public ScanDetail getScanDetail(String buildScanUrl) {
    //FIXME
    // dependency management (http + json)

    //FIXME HTTP
    // auth + dual auth plugin Vs job
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


    int scanPathIndex = buildScanUrl.lastIndexOf("/s/");
    if(scanPathIndex != -1){
      String baseApiUrl = buildScanUrl.substring(0, scanPathIndex);
      String scanId = buildScanUrl.substring(scanPathIndex + 3);

      //FIXME remove me
      baseApiUrl = baseApiUrl.replaceAll("localhost", "host.docker.internal");

      try(CloseableHttpClient httpclient = HttpClients.createDefault()) {
        HttpGet httpGetApiBuilds = new HttpGet(baseApiUrl + "/api/builds/" + scanId);
        addBearerAuth(httpGetApiBuilds);

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
          addBearerAuth(httpGetGradleAttributes);
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
                        .withUrl(buildScanUrl)
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
                        .withUrl(buildScanUrl)
                        .build();
                EntityUtils.consume(httpEntity);
              }
            }
          }
        }
        return scanDetail;
      } catch (IOException e) {
        //FIXME
        e.printStackTrace();
      }
    }

    return null;
  }

  private void addBearerAuth(HttpGet httpGetApiBuilds) {
    if (buildScanAccessToken != null) {
      httpGetApiBuilds.addHeader("Authorization", "Bearer " + buildScanAccessToken.getPlainText());
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
