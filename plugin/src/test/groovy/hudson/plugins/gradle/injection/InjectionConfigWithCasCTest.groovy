package hudson.plugins.gradle.injection


import hudson.plugins.gradle.AbstractIntegrationTest
import io.jenkins.plugins.casc.misc.ConfiguredWithCode
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule
import org.junit.Rule
import org.junit.rules.RuleChain
import spock.lang.Subject
import spock.lang.Unroll

@Unroll
@Subject(InjectionConfig.class)
class InjectionConfigWithCasCTest extends AbstractIntegrationTest {
    @Rule
    public final RuleChain rules = RuleChain.outerRule(noSpaceInTmpDirs).around(new JenkinsConfiguredWithCodeRule())

    @ConfiguredWithCode("injection-config.yml")
    def 'current configuration is readable with JCasC'() {
        expect:
        with(InjectionConfig.get()) {
            it.allowUntrusted == true
            it.ccudExtensionCustomCoordinates == "mycustom-ccud:ext"
            it.ccudExtensionVersion == "2.0.1"
            it.ccudPluginVersion == "2.0.2"
            it.checkForBuildAgentErrors == true
            it.enabled == true
            it.enforceUrl == true
            it.gradleCaptureTaskInputFiles == true
            it.gradleInjectionDisabledNodes*.label == ["non-gradle-node"]
            it.gradleInjectionEnabledNodes*.label == ["gradle-node"]
            it.gradlePluginRepositoryUrl == "https://plugins.gradle.org"
            it.gradlePluginVersion == "3.18.1"
            it.injectMavenExtension == true
            it.injectCcudExtension == true
            it.mavenCaptureGoalInputFiles == true
            it.mavenExtensionCustomCoordinates == "mycustom:ext"
            it.mavenExtensionRepositoryUrl == "https://repo1.maven.org/maven2"
            it.mavenExtensionVersion == "2.0"
            it.mavenInjectionDisabledNodes*.label == ["non-maven-node"]
            it.mavenInjectionEnabledNodes*.label == ["maven-node"]
            it.server == "http://localhost:5086"
            it.shortLivedTokenExpiry == 24
            it.vcsRepositoryFilter == "+:myrepo"
        }
    }
}
