package hudson.plugins.gradle.enriched

import hudson.util.Secret
import org.apache.http.HttpStatus
import org.apache.http.HttpVersion
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.message.BasicStatusLine
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Subject(ScanDetailService.class)
class ScanDetailServiceTest extends Specification {

    EnrichedSummaryConfig getTestConfig() {
        def config = Stub(EnrichedSummaryConfig.class)
        config.isEnrichedSummaryEnabled() >> true
        config.getBuildScanAccessKey() >> Secret.fromString("{c2VjcmV0}")
        config.getBuildScanServer() >> new URI("https://foo.bar")
        config
    }

    def 'Get scan detail with enriched summary feature disabled'() {
        given:
        def config = Stub(EnrichedSummaryConfig.class)
        config.isEnrichedSummaryEnabled() >> false
        config.getBuildScanAccessKey() >> Secret.fromString("{c2VjcmV0}")
        def scanDetailService = new ScanDetailService(config)

        when:
        def scanDetail = scanDetailService.getScanDetail("https://foo.bar")

        then:
        scanDetail == Optional.empty()
    }

    def 'Get scan detail with malformed build scan URL'() {
        given:
        def scanDetailService = new ScanDetailService(getTestConfig())

        when:
        def scanDetail = scanDetailService.getScanDetail("https://foo.bar")

        then:
        scanDetail == Optional.empty()
    }

    def 'Get scan detail with HTTP error on first request'() {
        given:
        def scanDetailService = new ScanDetailService(getTestConfig())
        def httpClientFactory = Stub(HttpClientFactory)
        scanDetailService.httpClientFactory = httpClientFactory
        def httpClient = Stub(CloseableHttpClient)
        def response = Stub(CloseableHttpResponse)
        response.getStatusLine() >> new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_FORBIDDEN, "")
        httpClientFactory.buildHttpClient(_,_,_) >> httpClient
        httpClient.execute(_) >> response

        when:
        def scanDetail = scanDetailService.getScanDetail("https://foo.bar/s/scanId")

        then:
        scanDetail == Optional.empty()
    }

    @Unroll
    def 'Get scan detail with HTTP error on second request'(String buildToolType) {
        given:
        def scanDetailService = new ScanDetailService(getTestConfig())
        def httpClientFactory = Stub(HttpClientFactory)
        scanDetailService.httpClientFactory = httpClientFactory
        def httpClient = Stub(CloseableHttpClient)
        def response1 = Stub(CloseableHttpResponse)
        response1.getStatusLine() >> new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "")
        response1.getEntity() >> new StringEntity(
                """
                    {
                        "buildToolType": "${buildToolType}",
                        "buildToolVersion": "7.5.1"   
                    }
                """.stripIndent())
        def response2 = Stub(CloseableHttpResponse)
        response2.getStatusLine() >> new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR, "")
        httpClientFactory.buildHttpClient(_,_,_) >> httpClient
        httpClient.execute(_) >>> [response1, response2]

        when:
        def scanDetail = scanDetailService.getScanDetail("https://foo.bar/s/scanId")

        then:
        scanDetail == Optional.empty()

        where:
        buildToolType << ["gradle", "maven"]
    }

    @Unroll
    def 'Get scan detail with malformed JSON'() {
        given:
        def scanDetailService = new ScanDetailService(getTestConfig())
        def httpClientFactory = Stub(HttpClientFactory)
        scanDetailService.httpClientFactory = httpClientFactory
        def httpClient = Stub(CloseableHttpClient)
        def response1 = Stub(CloseableHttpResponse)
        response1.getStatusLine() >> new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "")
        response1.getEntity() >> new StringEntity(
                """
                    {
                        "buildToolType": "${buildToolType}",
                        "buildToolVersion": "7.5.1"   
                    }
                """.stripIndent())
        def response2 = Stub(CloseableHttpResponse)
        response2.getStatusLine() >> new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "")
        response2.getEntity() >> new StringEntity("{This is not valid JSON}")
        httpClientFactory.buildHttpClient(_,_,_) >> httpClient
        httpClient.execute(_) >>> [response1, response2]

        when:
        def scanDetail = scanDetailService.getScanDetail("https://foo.bar/s/scanId")

        then:
        scanDetail == Optional.empty()

        where:
        buildToolType << ["gradle", "maven"]
    }

    @Unroll
    def 'Get scan detail with unrecognized JSON field'() {
        given:
        def scanDetailService = new ScanDetailService(getTestConfig())
        def httpClientFactory = Stub(HttpClientFactory)
        scanDetailService.httpClientFactory = httpClientFactory
        def httpClient = Stub(CloseableHttpClient)
        def response1 = Stub(CloseableHttpResponse)
        response1.getStatusLine() >> new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "")
        response1.getEntity() >> new StringEntity(
                """
                    {
                        "buildToolType": "${buildToolType}",
                        "buildToolVersion": "7.5.1"   
                    }
                """.stripIndent())
        def response2 = Stub(CloseableHttpResponse)
        response2.getStatusLine() >> new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "")
        response2.getEntity() >> new StringEntity(httpResponseBody)
        httpClientFactory.buildHttpClient(_,_,_) >> httpClient
        httpClient.execute(_) >>> [response1, response2]
        httpClientFactory.buildHttpClient(_,_,_) >> httpClient

        when:
        def scanDetailResult = scanDetailService.getScanDetail("https://foo.bar/s/scanId")

        then:
        def scanDetail = scanDetailResult.get()
        scanDetail.url == "https://foo.bar/s/scanId"
        scanDetail.buildToolType.toString() == buildToolType.toUpperCase()
        scanDetail.buildToolVersion == "7.5.1"
        scanDetail.projectName == "project"
        scanDetail.tasks == [ "clean", "build" ]
        !scanDetail.hasFailed

        where:
        buildToolType | httpResponseBody
        "gradle"      | '{"foo":"bar","rootProjectName":"project","requestedTasks":["clean","build"],"hasFailed":false}'
        "maven"       | '{"foo":"bar","topLevelProjectName":"project","requestedGoals":["clean","build"],"hasFailed":false}'
    }

    @Unroll
    def 'Get scan detail'(String buildToolType, String httpResponseBody) {
        given:
        def scanDetailService = new ScanDetailService(getTestConfig())
        def httpClientFactory = Stub(HttpClientFactory)
        scanDetailService.httpClientFactory = httpClientFactory
        def httpClient = Stub(CloseableHttpClient)
        def response1 = Stub(CloseableHttpResponse)
        response1.getStatusLine() >> new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "")
        response1.getEntity() >> new StringEntity(
                """
                    {
                        "buildToolType": "${buildToolType}",
                        "buildToolVersion": "7.5.1"   
                    }
                """.stripIndent())
        def response2 = Stub(CloseableHttpResponse)
        response2.getStatusLine() >> new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "")
        response2.getEntity() >> new StringEntity(httpResponseBody)
        httpClient.execute(_) >>> [response1, response2]
        httpClientFactory.buildHttpClient(_,_,_) >> httpClient

        when:
        def scanDetailResult = scanDetailService.getScanDetail("https://foo.bar/s/scanId")

        then:
        def scanDetail = scanDetailResult.get()
        scanDetail.url == "https://foo.bar/s/scanId"
        scanDetail.buildToolType.toString() == buildToolType.toUpperCase()
        scanDetail.buildToolVersion == "7.5.1"
        scanDetail.projectName == "project"
        scanDetail.tasks == [ "clean", "build" ]
        !scanDetail.hasFailed

        where:
        buildToolType | httpResponseBody
        "gradle"      | '{"foo":"bar","rootProjectName":"project","requestedTasks":["clean","build"],"hasFailed":false}'
        "maven"       | '{"topLevelProjectName":"project","requestedGoals":["clean","build"],"hasFailed":false}'
    }

}
