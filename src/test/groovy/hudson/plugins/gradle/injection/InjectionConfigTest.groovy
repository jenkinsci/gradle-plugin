package hudson.plugins.gradle.injection

import hudson.util.FormValidation
import hudson.util.XStream2
import spock.lang.Shared
import spock.lang.Subject
import spock.lang.Unroll

@Subject(InjectionConfig.class)
class InjectionConfigTest extends BaseGradleInjectionIntegrationTest {

    @Shared
    FilenameFilter injectionConfigXmlFilter = { _, name -> name == "hudson.plugins.gradle.injection.InjectionConfig.xml" }

    @Unroll
    def "validates server url"() {
        expect:
        with(InjectionConfig.get().doCheckServer(url)) {
            kind == expectedKind
            message == expectedMssage
        }

        where:
        url                      || expectedKind              | expectedMssage
        "http://gradle.com/test" || FormValidation.Kind.OK    | null
        "http://localhost"       || FormValidation.Kind.OK    | null
        "https://localhost"      || FormValidation.Kind.OK    | null
        "ftp://localhost"        || FormValidation.Kind.ERROR | "Not a valid URL."
        "localhost"              || FormValidation.Kind.ERROR | "Not a valid URL."
        ""                       || FormValidation.Kind.ERROR | "Required."
        null                     || FormValidation.Kind.ERROR | "Required."
    }

    def "saves injection configuration"() {
        given:
        def webClient = j.createWebClient()
        def page = webClient.goTo("configure")
        def form = page.getFormByName("config")

        when:
        form.getInputByName("_.enabled").click()
        form.getInputByName("_.server").setValueAttribute("https://localhost")
        form.getInputByName("_.allowUntrusted").click()
        form.getInputByName("_.accessKey").setValueAttribute("ACCESS_KEY")

        form.getInputByName("_.gradlePluginVersion").setValueAttribute("3.11.1")
        form.getInputByName("_.ccudPluginVersion").setValueAttribute("1.8")
        form.getInputByName("_.gradlePluginRepositoryUrl").setValueAttribute("https://localhost/repostiry")

        // We don't validate the values at the moment as they are used only as a trigger
        form.getInputByName("_.mavenExtensionVersion").setValueAttribute("foo")
        form.getInputByName("_.ccudExtensionVersion").setValueAttribute("bar")

        j.submit(form)

        then:
        def files = j.jenkins.root.listFiles(injectionConfigXmlFilter)
        files.length == 1
        with(fromXml(files.first().text)) {
            enabled
            server == "https://localhost"
            allowUntrusted
            accessKey == "ACCESS_KEY"

            gradlePluginVersion == "3.11.1"
            ccudPluginVersion == "1.8"
            gradlePluginRepositoryUrl == "https://localhost/repostiry"
            gradleInjectionEnabledNodes == null
            gradleInjectionDisabledNodes == null

            mavenExtensionVersion == "foo"
            ccudExtensionVersion == "bar"
            mavenInjectionEnabledNodes == null
            mavenInjectionDisabledNodes == null
        }
    }

    private static InjectionConfig fromXml(String xml) {
        return (InjectionConfig) new XStream2().fromXML(xml)
    }
}
