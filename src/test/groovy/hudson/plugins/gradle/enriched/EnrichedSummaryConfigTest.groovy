package hudson.plugins.gradle.enriched

import hudson.plugins.gradle.config.GlobalConfig
import hudson.plugins.gradle.injection.BaseGradleInjectionIntegrationTest
import hudson.util.FormValidation
import hudson.util.XStream2
import spock.lang.Shared
import spock.lang.Subject
import spock.lang.Unroll

@Subject(GlobalConfig.class)
class EnrichedSummaryConfigTest extends BaseGradleInjectionIntegrationTest {

    @Shared
    FilenameFilter enrichedSummaryConfigXmlFilter = { _, name -> name == "hudson.plugins.gradle.config.GlobalConfig.xml" }

    @Unroll
    def "validates HTTP client max retries"() {
        expect:
        with(GlobalConfig.get().doCheckHttpClientMaxRetries(httpClientMaxRetries)) {
            kind == expectedKind
            message == expectedMessage
        }

        where:
        httpClientMaxRetries || expectedKind              | expectedMessage
        10                   || FormValidation.Kind.OK    | null
        -1                   || FormValidation.Kind.ERROR | "Max retries must be in [0,20]."
        25                   || FormValidation.Kind.ERROR | "Max retries must be in [0,20]."
    }

    @Unroll
    def "validates HTTP client delay between retries"() {
        expect:
        with(GlobalConfig.get().doCheckHttpClientDelayBetweenRetriesInSeconds(httpClientDelayBetweenRetriesInSeconds)) {
            kind == expectedKind
            message == expectedMessage
        }

        where:
        httpClientDelayBetweenRetriesInSeconds || expectedKind              | expectedMessage
        10                                     || FormValidation.Kind.OK    | null
        -1                                     || FormValidation.Kind.ERROR | "Delay between retries must be in [0,20]."
        25                                     || FormValidation.Kind.ERROR | "Delay between retries must be in [0,20]."
    }

    @Unroll
    def "validates HTTP client timeout"() {
        expect:
        with(GlobalConfig.get().doCheckHttpClientTimeoutInSeconds(httpClientTimeoutInSeconds)) {
            kind == expectedKind
            message == expectedMessage
        }

        where:
        httpClientTimeoutInSeconds || expectedKind              | expectedMessage
        10                         || FormValidation.Kind.OK    | null
        -1                         || FormValidation.Kind.ERROR | "Timeout must be in [0,300]."
        350                        || FormValidation.Kind.ERROR | "Timeout must be in [0,300]."
    }

    def "saves enriched summary configuration"() {
        given:
        def webClient = j.createWebClient()
        def page = webClient.goTo("configure")
        def form = page.getFormByName("config")

        when:
        form.getInputByName("_.enrichedSummaryEnabled").click()
        form.getInputByName("_.buildScanServer").setValueAttribute("https://localhost")
        form.getInputByName("_.buildScanAccessKey").setValueAttribute("ACCESS_KEY")
        form.getInputByName("_.httpClientTimeoutInSeconds").setValueAttribute("30")
        form.getInputByName("_.httpClientMaxRetries").setValueAttribute("10")
        form.getInputByName("_.httpClientDelayBetweenRetriesInSeconds").setValueAttribute("60")
        j.submit(form)

        then:
        def files = j.jenkins.root.listFiles(enrichedSummaryConfigXmlFilter)
        files.length == 1
        with(fromXml(files.first().text)) {
            enrichedSummaryEnabled
            buildScanServer == "https://localhost"
            buildScanAccessKey.plainText == "ACCESS_KEY"

            httpClientTimeoutInSeconds == 30
            httpClientMaxRetries == 10
            httpClientDelayBetweenRetriesInSeconds == 60
        }
    }

    private static GlobalConfig fromXml(String xml) {
        return (GlobalConfig) new XStream2().fromXML(xml)
    }
}
