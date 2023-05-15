package hudson.plugins.gradle.injection

import com.gargoylesoftware.htmlunit.html.HtmlButton
import com.gargoylesoftware.htmlunit.html.HtmlForm
import hudson.plugins.gradle.BaseJenkinsIntegrationTest
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.util.FormValidation
import hudson.util.XStream2
import spock.lang.Shared
import spock.lang.Subject
import spock.lang.Unroll

import java.nio.charset.StandardCharsets

@Unroll
@Subject(InjectionConfig.class)
class InjectionConfigTest extends BaseJenkinsIntegrationTest {

    @Shared
    FilenameFilter injectionConfigXmlFilter = { _, name -> name == "hudson.plugins.gradle.injection.InjectionConfig.xml" }

    def "sets showLegacyConfigurationWarning to true if any of legacy env variables is set"() {
        given:
        def env = new EnvironmentVariablesNodeProperty()
        env.getEnvVars().put("TEST", "true")

        if (name != null) {
            env.getEnvVars().put(name, value)
        }

        j.jenkins.getGlobalNodeProperties().add(env)

        expect:
        InjectionConfig.get().isShowLegacyConfigurationWarning() == showWarning

        where:
        name                                                           | value               || showWarning
        null                                                           | null                || false
        "FOO"                                                          | "bar"               || false
        "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_INJECTION"              | "true"              || true
        "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL"                    | "https://localhost" || true
        "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER" | "true"              || true
        "GRADLE_ENTERPRISE_ACCESS_KEY"                                 | "foo"               || true
        "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION"         | "3.11.1"            || true
        "JENKINSGRADLEPLUGIN_CCUD_PLUGIN_VERSION"                      | "1.8.1"             || true
        "JENKINSGRADLEPLUGIN_GRADLE_PLUGIN_REPOSITORY_URL"             | "https://localhost" || true
        "JENKINSGRADLEPLUGIN_GRADLE_INJECTION_ENABLED_NODES"           | "foo,bar"           || true
        "JENKINSGRADLEPLUGIN_GRADLE_INJECTION_DISABLED_NODES"          | "foo,bar"           || true
        "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_EXTENSION_VERSION"      | "1.15.5"            || true
        "JENKINSGRADLEPLUGIN_CCUD_EXTENSION_VERSION"                   | "1.11.1"            || true
        "JENKINSGRADLEPLUGIN_MAVEN_INJECTION_ENABLED_NODES"            | "foo,bar"           || true
        "JENKINSGRADLEPLUGIN_MAVEN_INJECTION_DISABLED_NODES"           | "foo,bar"           || true
    }

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

    def "validates access key"() {
        expect:
        with(InjectionConfig.get().doCheckAccessKey(accessKey)) {
            kind == expectedKind
            message == expectedMssage
        }

        where:
        accessKey       || expectedKind              | expectedMssage
        null            || FormValidation.Kind.OK    | null
        ""              || FormValidation.Kind.OK    | null
        "secret"        || FormValidation.Kind.ERROR | "Not a valid access key."
        "server=secret" || FormValidation.Kind.OK    | null
    }

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

    def "saves injection configuration"() {
        given:
        def webClient = j.createWebClient()
        def page = webClient.goTo("configure")
        def form = page.getFormByName("config")

        when:
        form.getInputByName("_.enabled").click()
        form.getInputByName("_.server").setValueAttribute("https://localhost")
        form.getInputByName("_.allowUntrusted").click()
        form.getInputByName("_.accessKey").setValueAttribute("server=secret")

        form.getInputByName("_.gradlePluginVersion").setValueAttribute("3.11.1")
        form.getInputByName("_.ccudPluginVersion").setValueAttribute("1.8")
        form.getInputByName("_.gradlePluginRepositoryUrl").setValueAttribute("https://localhost/repostiry")

        getAddButton(form, "Gradle auto-injection enabled nodes").click()
        form.getInputsByName("_.label").last().setValueAttribute("gradle1")
        getAddButton(form, "Gradle auto-injection disabled nodes").click()
        form.getInputsByName("_.label").last().setValueAttribute("gradle2")

        form.getInputByName("_.injectMavenExtension").click()
        form.getInputByName("_.injectCcudExtension").click()

        getAddButton(form, "Maven auto-injection enabled nodes").click()
        form.getInputsByName("_.label").last().setValueAttribute("maven1")
        getAddButton(form, "Maven auto-injection disabled nodes").click()
        form.getInputsByName("_.label").last().setValueAttribute("maven2")

        j.submit(form)

        then:
        def files = j.jenkins.root.listFiles(injectionConfigXmlFilter)
        files.length == 1
        with(fromXml(files.first().text)) {
            enabled
            server == "https://localhost"
            allowUntrusted
            accessKey.plainText == "server=secret"

            gradlePluginVersion == "3.11.1"
            ccudPluginVersion == "1.8"
            gradlePluginRepositoryUrl == "https://localhost/repostiry"
            gradleInjectionEnabledNodes*.label == ['gradle1']
            gradleInjectionDisabledNodes*.label == ['gradle2']

            injectMavenExtension
            injectCcudExtension
            mavenInjectionEnabledNodes*.label == ['maven1']
            mavenInjectionDisabledNodes*.label == ['maven2']
        }
    }

    def "ignores empty access key"() {
        given:
        def webClient = j.createWebClient()
        def page = webClient.goTo("configure")
        def form = page.getFormByName("config")

        when:
        form.getInputByName("_.enabled").click()
        form.getInputByName("_.server").setValueAttribute("https://localhost")
        form.getInputByName("_.allowUntrusted").click()
        form.getInputByName("_.accessKey").setValueAttribute(accessKey)

        j.submit(form)

        then:
        def config = InjectionConfig.get()
        config.enabled
        config.server == "https://localhost"
        config.accessKey == null

        where:
        accessKey << ["", "   "]
    }

    def "migrates legacy VCS filter during loading of the config"() {
        given:
        def xml = """<?xml version="1.1" encoding="UTF-8"?>
<hudson.plugins.gradle.injection.InjectionConfig>
  <enabled>true</enabled>
  <server>https://localhost</server>
  <allowUntrusted>true</allowUntrusted>
  <gradlePluginVersion>3.11.1</gradlePluginVersion>
  <ccudPluginVersion>1.8</ccudPluginVersion>
  <gradlePluginRepositoryUrl>https://localhost/repostiry</gradlePluginRepositoryUrl>
  <gradleInjectionEnabledNodes>
    <hudson.plugins.gradle.injection.NodeLabelItem>
      <label>gradle1</label>
    </hudson.plugins.gradle.injection.NodeLabelItem>
  </gradleInjectionEnabledNodes>
  <gradleInjectionDisabledNodes>
    <hudson.plugins.gradle.injection.NodeLabelItem>
      <label>gradle2</label>
    </hudson.plugins.gradle.injection.NodeLabelItem>
  </gradleInjectionDisabledNodes>
  <injectMavenExtension>true</injectMavenExtension>
  <injectCcudExtension>true</injectCcudExtension>
  <mavenInjectionEnabledNodes>
    <hudson.plugins.gradle.injection.NodeLabelItem>
      <label>maven1</label>
    </hudson.plugins.gradle.injection.NodeLabelItem>
  </mavenInjectionEnabledNodes>
  <mavenInjectionDisabledNodes>
    <hudson.plugins.gradle.injection.NodeLabelItem>
      <label>maven2</label>
    </hudson.plugins.gradle.injection.NodeLabelItem>
  </mavenInjectionDisabledNodes>
  <injectionVcsRepositoryPatterns>foo, , bar, baz</injectionVcsRepositoryPatterns>
</hudson.plugins.gradle.injection.InjectionConfig>"""

        when:
        def config = fromXml(xml)

        then:
        with(config) {
            hasRepositoryFilter()
            vcsRepositoryFilter == "+:foo\n+:bar\n+:baz"
        }

        when:
        config.setVcsRepositoryFilter("+:foo\n-:bar")

        then:
        toXml(config) == """<?xml version="1.1" encoding="UTF-8"?>
<hudson.plugins.gradle.injection.InjectionConfig>
  <enabled>true</enabled>
  <server>https://localhost</server>
  <allowUntrusted>true</allowUntrusted>
  <gradlePluginVersion>3.11.1</gradlePluginVersion>
  <ccudPluginVersion>1.8</ccudPluginVersion>
  <gradlePluginRepositoryUrl>https://localhost/repostiry</gradlePluginRepositoryUrl>
  <gradleInjectionEnabledNodes>
    <hudson.plugins.gradle.injection.NodeLabelItem>
      <label>gradle1</label>
    </hudson.plugins.gradle.injection.NodeLabelItem>
  </gradleInjectionEnabledNodes>
  <gradleInjectionDisabledNodes>
    <hudson.plugins.gradle.injection.NodeLabelItem>
      <label>gradle2</label>
    </hudson.plugins.gradle.injection.NodeLabelItem>
  </gradleInjectionDisabledNodes>
  <injectMavenExtension>true</injectMavenExtension>
  <injectCcudExtension>true</injectCcudExtension>
  <mavenInjectionEnabledNodes>
    <hudson.plugins.gradle.injection.NodeLabelItem>
      <label>maven1</label>
    </hudson.plugins.gradle.injection.NodeLabelItem>
  </mavenInjectionEnabledNodes>
  <mavenInjectionDisabledNodes>
    <hudson.plugins.gradle.injection.NodeLabelItem>
      <label>maven2</label>
    </hudson.plugins.gradle.injection.NodeLabelItem>
  </mavenInjectionDisabledNodes>
  <enforceUrl>false</enforceUrl>
  <checkForBuildAgentErrors>false</checkForBuildAgentErrors>
  <parsedVcsRepositoryFilter>
    <vcsRepositoryFilter>+:foo
-:bar</vcsRepositoryFilter>
    <inclusion class="com.google.common.collect.ImmutableList">
      <string>foo</string>
    </inclusion>
    <exclusion class="com.google.common.collect.ImmutableList">
      <string>bar</string>
    </exclusion>
  </parsedVcsRepositoryFilter>
</hudson.plugins.gradle.injection.InjectionConfig>"""
    }

    private static InjectionConfig fromXml(String xml) {
        return (InjectionConfig) new XStream2().fromXML(xml)
    }

    private static String toXml(InjectionConfig config) {
        def out = new ByteArrayOutputStream()
        new XStream2().toXMLUTF8(config, out)
        return new String(out.toByteArray(), StandardCharsets.UTF_8)
    }

    private static HtmlButton getAddButton(HtmlForm form, String label) {
        def xpath = "//td[@class = 'setting-name' and text() = '$label']/following-sibling::td[@class = 'setting-main']//span[contains(@class, 'repeatable-add')]//button[text() = 'Add']"
        return form.getFirstByXPath(xpath)
    }
}
