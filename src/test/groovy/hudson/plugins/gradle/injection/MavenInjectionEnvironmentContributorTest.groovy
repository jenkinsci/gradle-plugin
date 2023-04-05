package hudson.plugins.gradle.injection

import hudson.EnvVars
import hudson.model.Run
import hudson.model.TaskListener
import hudson.plugins.gradle.BaseJenkinsIntegrationTest
import spock.lang.Subject

class MavenInjectionEnvironmentContributorTest extends BaseJenkinsIntegrationTest {

    def envs = new EnvVars()

    @Subject
    def mavenInjectionEnvironmentContributor = new MavenInjectionEnvironmentContributor()

    def "does nothing if injection is disabled and MAVEN_OPTS doesn't exist"() {
        given:
        withInjectionConfig {
            enabled = false
            server = null
            injectMavenExtension = false
            injectCcudExtension = false
            allowUntrusted = false
        }

        when:
        mavenInjectionEnvironmentContributor.buildEnvironmentFor(Mock(Run), envs, TaskListener.NULL)

        then:
        !envs.containsKey(MavenOptsHandler.MAVEN_OPTS)
        !envs.containsKey(MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH)
    }

    def "does not modify existing MAVEN_OPTS if injection is disabled"() {
        given:
        withInjectionConfig {
            enabled = false
            server = null
            injectMavenExtension = false
            injectCcudExtension = false
            allowUntrusted = false
        }

        envs.put(MavenOptsHandler.MAVEN_OPTS, "-Dmaven.ext.class.path=/tmp/custom-extension.jar")

        when:
        mavenInjectionEnvironmentContributor.buildEnvironmentFor(Mock(Run), envs, TaskListener.NULL)

        then:
        !envs.containsKey(MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH)

        envs.get(MavenOptsHandler.MAVEN_OPTS) == "-Dmaven.ext.class.path=/tmp/custom-extension.jar"
    }

    def "maven opts and maven plugin environment variables removed if injection is disabled due to vcs filtering"() {
        given:
        withInjectionConfig {
            enabled = true
            server = 'https://scans.gradle.com'
            injectMavenExtension = true
            injectCcudExtension = true
            allowUntrusted = true
        }
        envs.put(MavenOptsHandler.MAVEN_OPTS, "-Dfoo=bar")

        def mavenInjectionDisabledMavenOptsAction = new GitScmListener.MavenInjectionDisabledMavenOptsAction("") {}

        def mockRun = Mock(Run)
        mockRun.getAction(GitScmListener.MavenInjectionDisabledMavenOptsAction.class) >> mavenInjectionDisabledMavenOptsAction

        when:
        mavenInjectionEnvironmentContributor.buildEnvironmentFor(mockRun, envs, TaskListener.NULL)

        then:
        envs.get(MavenOptsHandler.MAVEN_OPTS) == ""
    }

}
