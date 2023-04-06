package hudson.plugins.gradle.injection

import hudson.EnvVars
import hudson.model.Run
import hudson.model.TaskListener
import hudson.plugins.gradle.BaseJenkinsIntegrationTest
import spock.lang.Subject

class GradleInjectionEnvironmentContributorTest extends BaseJenkinsIntegrationTest {

    def envs = new EnvVars()

    @Subject
    def gradleInjectionEnvironmentContributor = new GradleInjectionEnvironmentContributor()

    def "does nothing if injection is disabled"() {
        given:
        withInjectionConfig {
            enabled = false
            server = null
            gradlePluginVersion = null
            ccudPluginVersion = null
            gradlePluginRepositoryUrl = null
            allowUntrusted = false
        }

        when:
        gradleInjectionEnvironmentContributor.buildEnvironmentFor(Mock(Run), envs, TaskListener.NULL)

        then:
        !envs.containsKey(GradleInjectionEnvironmentContributor.JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_GRADLE_INJECTION_ENABLED)
    }

    def "disables injection if action is present"() {
        given:
        withInjectionConfig {
            enabled = true
            server = "http://localhost"
            gradlePluginVersion = "3.13"
            ccudPluginVersion = "1.8.2"
            gradlePluginRepositoryUrl = "http://localhost/repository"
            allowUntrusted = true
            injectMavenExtension = false
            injectCcudExtension = false
        }

        def gradleInjectionDisabledAction = GitScmListener.GradleInjectionDisabledAction.INSTANCE;

        def mockRun = Mock(Run)
        mockRun.getAction(GitScmListener.GradleInjectionDisabledAction.class) >> gradleInjectionDisabledAction

        when:
        gradleInjectionEnvironmentContributor.buildEnvironmentFor(mockRun, envs, TaskListener.NULL)

        then:
        envs.get(GradleInjectionAware.JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_GRADLE_INJECTION_ENABLED) == "false"
    }

}
