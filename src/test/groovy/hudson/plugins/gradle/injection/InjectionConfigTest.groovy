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
        "  https://localhost"    || FormValidation.Kind.OK    | null
        "https://localhost  "    || FormValidation.Kind.OK    | null
        "ftp://localhost"        || FormValidation.Kind.ERROR | "Not a valid URL."
        "localhost"              || FormValidation.Kind.ERROR | "Not a valid URL."
        ""                       || FormValidation.Kind.ERROR | "Required."
        null                     || FormValidation.Kind.ERROR | "Required."
    }

    @Unroll
    def "validates plugin repository url"() {
        expect:
        with(InjectionConfig.get().doCheckGradlePluginRepositoryUrl(url)) {
            kind == expectedKind
            message == expectedMssage
        }

        where:
        url                      || expectedKind              | expectedMssage
        "http://gradle.com/test" || FormValidation.Kind.OK    | null
        "http://localhost"       || FormValidation.Kind.OK    | null
        "https://localhost"      || FormValidation.Kind.OK    | null
        "  https://localhost"    || FormValidation.Kind.OK    | null
        "https://localhost  "    || FormValidation.Kind.OK    | null
        "ftp://localhost"        || FormValidation.Kind.ERROR | "Not a valid URL."
        "localhost"              || FormValidation.Kind.ERROR | "Not a valid URL."
        ""                       || FormValidation.Kind.OK    | null
        null                     || FormValidation.Kind.OK    | null
    }

    @Unroll
    def "validates gradle plugin and ccud plugin version"() {
        expect:
        with(InjectionConfig.get().doCheckGradlePluginVersion(version)) {
            kind == expectedKind
            message == expectedMssage
        }
        with(InjectionConfig.get().doCheckCcudPluginVersion(version)) {
            kind == expectedKind
            message == expectedMssage
        }

        where:
        version                   || expectedKind              | expectedMssage
        "1"                       || FormValidation.Kind.ERROR | "Not a valid version."
        "1.0"                     || FormValidation.Kind.OK    | null
        "1.1.1"                   || FormValidation.Kind.OK    | null
        "   1.1.1"                || FormValidation.Kind.OK    | null
        "1.0   "                  || FormValidation.Kind.OK    | null
        "2.0.0-SNAPSHOT"          || FormValidation.Kind.OK    | null
        "2.0.0-my-branch_42-test" || FormValidation.Kind.OK    | null
        "first"                   || FormValidation.Kind.ERROR | "Not a valid version."
    }

    def "allows any value for maven and ccud extensions"() {
        expect:
        with(InjectionConfig.get().doCheckMavenExtensionVersion(version)) {
            kind == FormValidation.Kind.OK
            message == null
        }
        with(InjectionConfig.get().doCheckCcudExtensionVersion(version)) {
            kind == FormValidation.Kind.OK
            message == null
        }

        where:
        version << [null, "", "1", "1.0", "1.1.1", "first"]
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
