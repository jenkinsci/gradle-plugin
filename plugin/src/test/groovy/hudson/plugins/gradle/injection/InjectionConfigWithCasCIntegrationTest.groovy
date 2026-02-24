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
class InjectionConfigWithCasCIntegrationTest extends AbstractIntegrationTest {

    @Rule
    public final RuleChain rules = RuleChain.outerRule(noSpaceInTmpDirs).around(new JenkinsConfiguredWithCodeRule())

    @ConfiguredWithCode("injection-config.yml")
    def 'current configuration is readable with JCasC'() {
        expect:
        with(InjectionConfig.get()) {
            allowUntrusted
            ccudExtensionCustomCoordinates == "mycustom-ccud:ext"
            ccudExtensionVersion == "2.0.1"
            ccudPluginVersion == "2.0.2"
            checkForBuildAgentErrors
            enabled
            enforceUrl
            gradleCaptureTaskInputFiles
            gradleInjectionDisabledNodes*.label == ["non-gradle-node"]
            gradleInjectionEnabledNodes*.label == ["gradle-node"]
            gradlePluginRepositoryUrl == "https://plugins.gradle.org"
            gradlePluginVersion == "3.18.1"
            injectMavenExtension
            injectCcudExtension
            mavenCaptureGoalInputFiles == true
            mavenExtensionCustomCoordinates == "mycustom:ext"
            mavenExtensionRepositoryUrl == "https://repo1.maven.org/maven2"
            mavenExtensionVersion == "2.1"
            mavenInjectionDisabledNodes*.label == ["non-maven-node"]
            mavenInjectionEnabledNodes*.label == ["maven-node"]
            npmAgentVersion == "3.0.0"
            npmAgentRegistryUrl == "https://registry.npmjs.org"
            npmInjectionDisabledNodes*.label == ["non-npm-node"]
            npmInjectionEnabledNodes*.label == ["npm-node"]
            server == "http://localhost:5086"
            shortLivedTokenExpiry == 24
            vcsRepositoryFilter == "+:myrepo"
        }
    }
}
