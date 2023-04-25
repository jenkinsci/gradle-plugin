package hudson.plugins.gradle.injection

import hudson.EnvVars
import hudson.model.PasswordParameterValue
import hudson.model.Run
import hudson.model.TaskListener
import hudson.plugins.gradle.BaseJenkinsIntegrationTest
import hudson.plugins.gradle.injection.BuildScanEnvironmentContributor.GradleEnterpriseParametersAction
import hudson.util.Secret
import spock.lang.Subject

class BuildScanEnvironmentContributorTest extends BaseJenkinsIntegrationTest {

    def run = Mock(Run)

    @Subject
    def buildScanEnvironmentContributor = new BuildScanEnvironmentContributor()

    def 'does nothing if no access key'() {
        given:
        def config = InjectionConfig.get()
        config.setAccessKey(Secret.fromString(""))
        config.save()

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(run, new EnvVars(), TaskListener.NULL)

        then:
        0 * run.addAction(_)
    }

    def 'adds empty action if access key is invalid'() {
        given:
        def config = InjectionConfig.get()
        config.setAccessKey(Secret.fromString("secret"))
        config.save()

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(run, new EnvVars(), TaskListener.NULL)

        then:
        1 * run.addAction { action ->
            action.is(GradleEnterpriseParametersAction.empty())
        }
    }

    def 'adds an action with the access key'() {
        given:
        def accessKey = "server=secret"
        def config = InjectionConfig.get()
        config.setAccessKey(Secret.fromString(accessKey))
        config.save()

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(run, new EnvVars(), TaskListener.NULL)

        then:
        1 * run.addAction { GradleEnterpriseParametersAction action ->
            def parameters = action.getAllParameters()
            parameters.size() == 1
            parameters.first() == new PasswordParameterValue('GRADLE_ENTERPRISE_ACCESS_KEY', accessKey, null)
        }
    }
}
