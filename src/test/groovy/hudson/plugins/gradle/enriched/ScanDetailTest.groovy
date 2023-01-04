package hudson.plugins.gradle.enriched

import hudson.util.Secret
import org.apache.http.HttpStatus
import org.apache.http.HttpVersion
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.message.BasicStatusLine
import spock.lang.Specification

class ScanDetailTest extends Specification {

    def 'Get scan detail with enriched summary feature disabled'() {
        given:
        def enrichedSummaryConfig = Stub(EnrichedSummaryConfig)
        def scanDetailService = new ScanDetailServiceDefaultImpl(Secret.fromString("{c2VjcmV0}"), new URI("https://foo.bar"))
        scanDetailService.enrichedSummaryConfig = enrichedSummaryConfig
        enrichedSummaryConfig.isEnabled() >> false

        when:
        def scanDetail = scanDetailService.getScanDetail("https://foo.bar")

        then:
        scanDetail == null
    }

    def 'Get scan detail with malformed build scan URL'() {
        given:
        def enrichedSummaryConfig = Stub(EnrichedSummaryConfig)
        def scanDetailService = new ScanDetailServiceDefaultImpl(Secret.fromString("{c2VjcmV0}"), new URI("https://foo.bar"))
        scanDetailService.enrichedSummaryConfig = enrichedSummaryConfig
        enrichedSummaryConfig.isEnabled() >> true

        when:
        def scanDetail = scanDetailService.getScanDetail("https://foo.bar")

        then:
        scanDetail == null
    }

    def 'Get scan detail with HTTP error on first request'() {
        given:
        def httpClientProvider = Stub(HttpClientProvider)
        def enrichedSummaryConfig = Stub(EnrichedSummaryConfig)
        def scanDetailService = new ScanDetailServiceDefaultImpl(Secret.fromString("{c2VjcmV0}"), new URI("https://foo.bar"))
        scanDetailService.httpClientProvider = httpClientProvider
        scanDetailService.enrichedSummaryConfig = enrichedSummaryConfig
        enrichedSummaryConfig.isEnabled() >> true
        CloseableHttpClient httpClient = Stub(CloseableHttpClient)
        def response1 = Stub(CloseableHttpResponse)
        response1.getStatusLine() >> new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_FORBIDDEN, "")
        httpClient.execute(_) >>> response1
        httpClientProvider.buildHttpClient() >> httpClient

        when:
        def scanDetail = scanDetailService.getScanDetail("https://foo.bar/s/scanId")

        then:
        scanDetail == null
    }

    def 'Get scan detail with HTTP error on second request'(String buildToolType) {
        given:
        def httpClientProvider = Stub(HttpClientProvider)
        def enrichedSummaryConfig = Stub(EnrichedSummaryConfig)
        def scanDetailService = new ScanDetailServiceDefaultImpl(Secret.fromString("{c2VjcmV0}"), new URI("https://foo.bar"))
        scanDetailService.httpClientProvider = httpClientProvider
        scanDetailService.enrichedSummaryConfig = enrichedSummaryConfig
        enrichedSummaryConfig.isEnabled() >> true
        CloseableHttpClient httpClient = Stub(CloseableHttpClient)
        def response1 = Stub(CloseableHttpResponse)
        response1.getStatusLine() >> new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "")
        response1.getEntity() >> new StringEntity("{\"buildToolType\":\"" + buildToolType + "\",\"buildToolVersion\":\"7.5.1\"}")
        def response2 = Stub(CloseableHttpResponse)
        response2.getStatusLine() >> new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR, "")
        httpClient.execute(_) >>> [response1, response2]
        httpClientProvider.buildHttpClient() >> httpClient

        when:
        def scanDetail = scanDetailService.getScanDetail("https://foo.bar/s/scanId")

        then:
        scanDetail == null

        where:
        buildToolType << ["gradle", "maven"]
    }

    def 'Get scan detail with malformed JSON'() {
        given:
        def httpClientProvider = Stub(HttpClientProvider)
        def enrichedSummaryConfig = Stub(EnrichedSummaryConfig)
        def scanDetailService = new ScanDetailServiceDefaultImpl(Secret.fromString("{c2VjcmV0}"), new URI("https://foo.bar"))
        scanDetailService.httpClientProvider = httpClientProvider
        scanDetailService.enrichedSummaryConfig = enrichedSummaryConfig
        enrichedSummaryConfig.isEnabled() >> true
        CloseableHttpClient httpClient = Stub(CloseableHttpClient)
        def response1 = Stub(CloseableHttpResponse)
        response1.getStatusLine() >> new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "")
        response1.getEntity() >> new StringEntity("{\"buildToolType\":\"" + buildToolType + "\",\"buildToolVersion\":\"7.5.1\"}")
        def response2 = Stub(CloseableHttpResponse)
        response2.getStatusLine() >> new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "")
        response2.getEntity() >> new StringEntity("{This is not valid JSON}")
        httpClient.execute(_) >>> [response1, response2]
        httpClientProvider.buildHttpClient() >> httpClient

        when:
        def scanDetail = scanDetailService.getScanDetail("https://foo.bar/s/scanId")

        then:
        scanDetail == null

        where:
        buildToolType << ["gradle", "maven"]
    }

    def 'Get scan detail'(String buildToolType, String httpResponseBody) {
        given:
        def httpClientProvider = Stub(HttpClientProvider)
        def enrichedSummaryConfig = Stub(EnrichedSummaryConfig)
        def scanDetailService = new ScanDetailServiceDefaultImpl(Secret.fromString("{c2VjcmV0}"), new URI("https://foo.bar"))
        scanDetailService.httpClientProvider = httpClientProvider
        scanDetailService.enrichedSummaryConfig = enrichedSummaryConfig
        enrichedSummaryConfig.isEnabled() >> true
        CloseableHttpClient httpClient = Stub(CloseableHttpClient)
        def response1 = Stub(CloseableHttpResponse)
        response1.getStatusLine() >> new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "")
        response1.getEntity() >> new StringEntity("{\"buildToolType\":\"" + buildToolType + "\",\"buildToolVersion\":\"7.5.1\"}")
        def response2 = Stub(CloseableHttpResponse)
        response2.getStatusLine() >> new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "")
        response2.getEntity() >> new StringEntity(httpResponseBody)
        httpClient.execute(_) >>> [response1, response2]
        httpClientProvider.buildHttpClient() >> httpClient

        when:
        def scanDetail = scanDetailService.getScanDetail("https://foo.bar/s/scanId")

        then:
        scanDetail.url == "https://foo.bar/s/scanId"
        scanDetail.buildToolType == buildToolType
        scanDetail.buildToolVersion == "7.5.1"
        scanDetail.projectName == "project"
        scanDetail.requestedTasks == "clean, build"
        !scanDetail.hasFailed

        where:
        buildToolType | httpResponseBody
        "gradle"      | "{\"rootProjectName\":\"project\",\"requestedTasks\":[\"clean\",\"build\"],\"hasFailed\":false}"
        "maven"       | "{\"topLevelProjectName\":\"project\",\"requestedGoals\":[\"clean\",\"build\"],\"hasFailed\":false}"
    }

}
