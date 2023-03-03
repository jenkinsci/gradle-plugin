package hudson.plugins.gradle.injection

import hudson.EnvVars
import hudson.model.Run
import hudson.model.TaskListener
import hudson.plugins.gradle.BaseJenkinsIntegrationTest
import hudson.util.Secret
import spock.lang.Subject

class BuildScanEnvironmentContributorTest extends BaseJenkinsIntegrationTest {

    def envs = new EnvVars()

    @Subject
    def buildScanEnvironmentContributor = new BuildScanEnvironmentContributor()

    def 'does nothing if no access key'() {
        given:
        def config = InjectionConfig.get()
        config.setAccessKey(Secret.fromString(""))
        config.save()

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(Mock(Run), envs, TaskListener.NULL)

        then:
        !envs.containsKey(BuildScanEnvironmentContributor.GRADLE_ENTERPRISE_ACCESS_KEY)
    }

    def 'does nothing if access key is invalid'() {
        given:
        def config = InjectionConfig.get()
        config.setAccessKey(Secret.fromString("secret"))
        config.save()

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(Mock(Run), envs, TaskListener.NULL)

        then:
        !envs.containsKey(BuildScanEnvironmentContributor.GRADLE_ENTERPRISE_ACCESS_KEY)
    }

    def 'adds access key to the environment'() {
        given:
        def accessKey = "server=secret"
        def config = InjectionConfig.get()
        config.setAccessKey(Secret.fromString(accessKey))
        config.save()

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(Mock(Run), envs, TaskListener.NULL)

        then:
        envs.get(BuildScanEnvironmentContributor.GRADLE_ENTERPRISE_ACCESS_KEY) == accessKey
    }
}
