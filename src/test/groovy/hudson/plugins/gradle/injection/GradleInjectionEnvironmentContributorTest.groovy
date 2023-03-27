package hudson.plugins.gradle.injection

import hudson.EnvVars
import hudson.model.Computer
import hudson.model.Executor
import hudson.model.Node
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
        !envs.containsKey(GradleInjectionEnvironmentContributor.GRADLE_ENTERPRISE_GRADLE_INJECTION_ENABLED)
        !envs.containsKey(GradleInjectionEnvironmentContributor.JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL)
        !envs.containsKey(GradleInjectionEnvironmentContributor.JENKINSGRADLEPLUGIN_CCUD_PLUGIN_VERSION)
        !envs.containsKey(GradleInjectionEnvironmentContributor.JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER)
        !envs.containsKey(GradleInjectionEnvironmentContributor.JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION)
        !envs.containsKey(GradleInjectionEnvironmentContributor.JENKINSGRADLEPLUGIN_GRADLE_PLUGIN_REPOSITORY_URL)
    }

    def "set all necessary environment variables if injection is enabled"() {
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

        def mockComputer = Mock(Computer)
        def mockRun = Mock(Run)
        mockComputer.getNode() >> Mock(Node)
        mockRun.getExecutor() >> new Executor(mockComputer, 0)

        when:
        gradleInjectionEnvironmentContributor.buildEnvironmentFor(mockRun, envs, TaskListener.NULL)

        then:
        envs.containsKey(GradleInjectionEnvironmentContributor.GRADLE_ENTERPRISE_GRADLE_INJECTION_ENABLED)
        envs.containsKey(GradleInjectionEnvironmentContributor.JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL)
        envs.containsKey(GradleInjectionEnvironmentContributor.JENKINSGRADLEPLUGIN_CCUD_PLUGIN_VERSION)
        envs.containsKey(GradleInjectionEnvironmentContributor.JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER)
        envs.containsKey(GradleInjectionEnvironmentContributor.JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION)
        envs.containsKey(GradleInjectionEnvironmentContributor.JENKINSGRADLEPLUGIN_GRADLE_PLUGIN_REPOSITORY_URL)
    }

}
