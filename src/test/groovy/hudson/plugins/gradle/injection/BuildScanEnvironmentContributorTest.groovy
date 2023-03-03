package hudson.plugins.gradle.injection

import hudson.EnvVars
import hudson.model.Run
import hudson.model.TaskListener
import hudson.util.Secret
import spock.lang.Specification
import spock.lang.Subject

class BuildScanEnvironmentContributorTest extends Specification {

    def envs = new EnvVars()
    def injectionConfig = Mock(InjectionConfig)
    @Subject
    def buildScanEnvironmentContributor = new BuildScanEnvironmentContributor({ injectionConfig })

    def 'does nothing if no access key'() {
        given:
        injectionConfig.getAccessKey() >> null

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(Mock(Run), envs, TaskListener.NULL)

        then:
        !envs.containsKey(BuildScanEnvironmentContributor.GRADLE_ENTERPRISE_ACCESS_KEY)
    }

    def 'does nothing if access key is invalid'() {
        given:
        injectionConfig.getAccessKey() >> Secret.fromString('secret')

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(Mock(Run), envs, TaskListener.NULL)

        then:
        !envs.containsKey(BuildScanEnvironmentContributor.GRADLE_ENTERPRISE_ACCESS_KEY)
    }

    def 'adds access key to the environment'() {
        given:
        def accessKey = 'server=secret'
        injectionConfig.getAccessKey() >> Secret.fromString(accessKey)

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(Mock(Run), envs, TaskListener.NULL)

        then:
        envs.get(BuildScanEnvironmentContributor.GRADLE_ENTERPRISE_ACCESS_KEY) == accessKey
    }
}
