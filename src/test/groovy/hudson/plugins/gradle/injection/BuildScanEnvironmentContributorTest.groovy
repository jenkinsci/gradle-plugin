package hudson.plugins.gradle.injection

import hudson.EnvVars
import hudson.model.ParameterValue
import hudson.model.Run
import hudson.model.TaskListener
import hudson.plugins.gradle.BaseJenkinsIntegrationTest
import hudson.plugins.gradle.injection.BuildScanEnvironmentContributor.DevelocityParametersAction
import hudson.plugins.gradle.injection.token.ShortLivedTokenClient
import hudson.util.Secret
import spock.lang.Subject

class BuildScanEnvironmentContributorTest extends BaseJenkinsIntegrationTest {

    def run = Mock(Run)
    def shortLivedTokenClient = Mock(ShortLivedTokenClient)

    @Subject
    def buildScanEnvironmentContributor = new BuildScanEnvironmentContributor(shortLivedTokenClient)

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

    def 'does nothing if no password'() {
        given:
        def config = InjectionConfig.get()
        config.setGradlePluginRepositoryPassword(Secret.fromString(""))
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
            action.is(DevelocityParametersAction.empty())
        }
    }

    def 'adds action if access key is invalid but password is there'() {
        given:
        def config = InjectionConfig.get()
        config.setAccessKey(Secret.fromString("secret"))
        config.setGradlePluginRepositoryPassword(Secret.fromString("foo"))
        config.save()

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(run, new EnvVars(), TaskListener.NULL)

        then:
        1 * run.addAction { DevelocityParametersAction action ->
            def parameters = action.getAllParameters()
            parameters.size() == 1
            paramEquals(parameters.first(), 'GRADLE_PLUGIN_REPOSITORY_PASSWORD', 'foo')
        }
    }

    def 'adds an action with the short lived token'() {
        given:
        def accessKey = "localhost=secret"
        def config = InjectionConfig.get()
        config.setServer('http://localhost')
        config.setAccessKey(Secret.fromString(accessKey))
        config.save()
        def key = DevelocityAccessCredentials.parse(accessKey, 'localhost').get()


        shortLivedTokenClient.get(config.getServer(), key, null) >> Optional.of(DevelocityAccessCredentials.of('localhost', 'xyz'))

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(run, new EnvVars(), TaskListener.NULL)

        then:
        1 * run.addAction { DevelocityParametersAction action ->
            def parameters = action.getAllParameters()
            parameters.size() == 2
            paramEquals(parameters[0], 'GRADLE_ENTERPRISE_ACCESS_KEY', 'localhost=xyz')
            paramEquals(parameters[1], 'DEVELOCITY_ACCESS_KEY', 'localhost=xyz')
        }
    }

    def 'adds an action with the password'() {
        given:
        def config = InjectionConfig.get()
        config.setGradlePluginRepositoryPassword(Secret.fromString("foo"))
        config.save()

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(run, new EnvVars(), TaskListener.NULL)

        then:
        1 * run.addAction { DevelocityParametersAction action ->
            def parameters = action.getAllParameters()
            parameters.size() == 1
            paramEquals(parameters.first(), 'GRADLE_PLUGIN_REPOSITORY_PASSWORD', 'foo')
        }
    }

    def 'adds an action with short lived token and password'() {
        given:
        def config = InjectionConfig.get()
        config.setServer('http://localhost')

        def accessKey = "localhost=secret"
        config.setAccessKey(Secret.fromString(accessKey))
        config.setGradlePluginRepositoryPassword(Secret.fromString("foo"))
        config.save()
        def key = DevelocityAccessCredentials.parse(accessKey, 'localhost').get()

        shortLivedTokenClient.get(config.getServer(), key, null) >> Optional.of(DevelocityAccessCredentials.of('localhost', 'xyz'))


        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(run, new EnvVars(), TaskListener.NULL)

        then:
        1 * run.addAction { DevelocityParametersAction action ->
            def parameters = action.getAllParameters()
            parameters.size() == 3
            paramEquals(parameters[0], 'GRADLE_ENTERPRISE_ACCESS_KEY', 'localhost=xyz')
            paramEquals(parameters[1], 'DEVELOCITY_ACCESS_KEY', 'localhost=xyz')
            paramEquals(parameters[2], 'GRADLE_PLUGIN_REPOSITORY_PASSWORD', 'foo')
        }
    }

    private boolean paramEquals(ParameterValue param, String name, String value) {
        param.name == name && param.value?.plainText == value
    }

}
