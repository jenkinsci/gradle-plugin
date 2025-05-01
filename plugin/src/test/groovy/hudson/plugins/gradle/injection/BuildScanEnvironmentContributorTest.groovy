package hudson.plugins.gradle.injection

import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
import hudson.EnvVars
import hudson.model.ParameterValue
import hudson.model.Run
import hudson.model.TaskListener
import hudson.plugins.gradle.BaseJenkinsIntegrationTest
import hudson.plugins.gradle.injection.BuildScanEnvironmentContributor.DevelocityParametersAction
import hudson.plugins.gradle.injection.token.ShortLivedTokenClient
import hudson.util.Secret
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl
import spock.lang.Subject

class BuildScanEnvironmentContributorTest extends BaseJenkinsIntegrationTest {

    def run = Mock(Run)
    def shortLivedTokenClient = Mock(ShortLivedTokenClient)

    @Subject
    def buildScanEnvironmentContributor = new BuildScanEnvironmentContributor(shortLivedTokenClient)

    def 'does nothing if no access key'() {
        given:
        def config = InjectionConfig.get()
        config.setEnabled(true)
        config.setAccessKeyCredentialId("")
        config.save()

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(run, new EnvVars(), TaskListener.NULL)

        then:
        0 * run.addAction(_)
    }

    def 'does nothing if no password'() {
        given:
        def config = InjectionConfig.get()
        config.setEnabled(true)
        config.setGradlePluginRepositoryCredentialId("")
        config.save()

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(run, new EnvVars(), TaskListener.NULL)

        then:
        0 * run.addAction(_)
    }

    def 'does nothing if Develocity injection is disabled'() {
        given:
        def config = InjectionConfig.get()
        config.setEnabled(false)
        config.save()

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(run, new EnvVars(), TaskListener.NULL)

        then:
        0 * run.addAction(_)
    }

    def 'adds empty action if access key is invalid'() {
        given:
        def config = InjectionConfig.get()
        config.setEnabled(true)
        def accessKeyCredentialId = UUID.randomUUID().toString()
        config.setAccessKeyCredentialId(accessKeyCredentialId)
        config.save()

        createStringCredentials(accessKeyCredentialId, "invalidSecret")

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(run, new EnvVars(), TaskListener.NULL)

        then:
        // Each query for finding the credential by ID creates an action
        2 * run.addAction { action ->
            if (action instanceof DevelocityParametersAction) {
                action.is(DevelocityParametersAction.empty())
            }
        }
    }

    def 'adds action if access key is invalid but password is there'() {
        given:
        def config = InjectionConfig.get()
        config.setEnabled(true)
        def accessKeyCredentialId = UUID.randomUUID().toString()
        def repositoryPasswordCredentialId = UUID.randomUUID().toString()
        config.setAccessKeyCredentialId(accessKeyCredentialId)
        config.setGradlePluginRepositoryCredentialId(repositoryPasswordCredentialId)
        config.save()

        createStringCredentials(accessKeyCredentialId, "invalidSecret")
        createUsernamePasswordCredentials(repositoryPasswordCredentialId, "john", "foo")

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(run, new EnvVars(), TaskListener.NULL)

        then:
        // Each query for finding the credential by ID creates an action
        3 * run.addAction { action ->
            if (action instanceof DevelocityParametersAction) {
                def parameters = action.getAllParameters()
                parameters.size() == 1
                paramEquals(parameters.first(), 'DEVELOCITY_INJECTION_PLUGIN_REPOSITORY_PASSWORD', 'foo')
            }
        }
    }

    def 'adds an action with the short lived token from one single access key'() {
        given:
        def accessKey = "localhost=secret"
        def accessKeyCredentialId = UUID.randomUUID().toString()
        def config = InjectionConfig.get()
        config.setEnabled(true)
        config.setServer('http://localhost')
        config.setAccessKeyCredentialId(accessKeyCredentialId)
        config.save()

        createStringCredentials(accessKeyCredentialId, accessKey)

        def key = DevelocityAccessCredentials.parse(accessKey).find('localhost').get()

        shortLivedTokenClient.get(config.getServer(), key, null) >> Optional.of(DevelocityAccessCredentials.HostnameAccessKey.of('localhost', 'xyz'))

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(run, new EnvVars(), TaskListener.NULL)

        then:
        // Each query for finding the credential by ID creates an action
        2 * run.addAction { action ->
            if (action instanceof DevelocityParametersAction) {
                def parameters = action.getAllParameters()
                parameters.size() == 2
                paramEquals(parameters[0], 'GRADLE_ENTERPRISE_ACCESS_KEY', 'localhost=xyz')
                paramEquals(parameters[1], 'DEVELOCITY_ACCESS_KEY', 'localhost=xyz')
            }
        }
    }

    def 'adds an action with the short lived token with multiple keys and enforce url is true'() {
        given:
        def accessKey = "localhost=secret;other=secret2"
        def accessKeyCredentialId = UUID.randomUUID().toString()
        def config = InjectionConfig.get()
        config.setEnabled(true)
        config.setServer('http://localhost')
        config.setEnforceUrl(true)
        config.setAccessKeyCredentialId(accessKeyCredentialId)
        config.save()

        createStringCredentials(accessKeyCredentialId, accessKey)

        def key = DevelocityAccessCredentials.parse(accessKey).find('localhost').get()

        shortLivedTokenClient.get(config.getServer(), key, null) >> Optional.of(DevelocityAccessCredentials.HostnameAccessKey.of('localhost', 'xyz'))

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(run, new EnvVars(), TaskListener.NULL)

        then:
        2 * run.addAction { action ->
            if (action instanceof DevelocityParametersAction) {
                def parameters = action.getAllParameters()
                parameters.size() == 2
                paramEquals(parameters[0], 'GRADLE_ENTERPRISE_ACCESS_KEY', 'localhost=xyz')
                paramEquals(parameters[1], 'DEVELOCITY_ACCESS_KEY', 'localhost=xyz')
            }
        }
    }

    def 'adds an action with the short lived token with multiple keys and enforce url is false'() {
        given:
        def accessKey = "localhost=secret;other=secret2"
        def accessKeyCredentialId = UUID.randomUUID().toString()
        def config = InjectionConfig.get()
        config.setEnabled(true)
        config.setServer('http://localhost')
        config.setEnforceUrl(false)
        config.setAccessKeyCredentialId(accessKeyCredentialId)
        config.save()

        createStringCredentials(accessKeyCredentialId, accessKey)

        shortLivedTokenClient.get("https://localhost", DevelocityAccessCredentials.HostnameAccessKey.of('localhost', 'secret'), null) >> Optional.of(DevelocityAccessCredentials.HostnameAccessKey.of('localhost', 'xyz'))
        shortLivedTokenClient.get("https://other", DevelocityAccessCredentials.HostnameAccessKey.of('other', 'secret2'), null) >> Optional.of(DevelocityAccessCredentials.HostnameAccessKey.of('other', 'abc'))

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(run, new EnvVars(), TaskListener.NULL)

        then:
        2 * run.addAction { action ->
            if (action instanceof DevelocityParametersAction) {
                def parameters = action.getAllParameters()
                parameters.size() == 2
                paramEquals(parameters[0], 'GRADLE_ENTERPRISE_ACCESS_KEY', 'localhost=xyz;other=abc')
                paramEquals(parameters[1], 'DEVELOCITY_ACCESS_KEY', 'localhost=xyz;other=abc')
            }
        }
    }

    def 'adds an action with the password'() {
        given:
        def passwordCredentialId = UUID.randomUUID().toString()
        def config = InjectionConfig.get()
        config.setEnabled(true)
        config.setGradlePluginRepositoryCredentialId(passwordCredentialId)
        config.save()

        createUsernamePasswordCredentials(passwordCredentialId, "john", "foo")

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(run, new EnvVars(), TaskListener.NULL)

        then:
        2 * run.addAction { action ->
            if (action instanceof DevelocityParametersAction) {
                def parameters = action.getAllParameters()
                parameters.size() == 1
                paramEquals(parameters.first(), 'DEVELOCITY_INJECTION_PLUGIN_REPOSITORY_PASSWORD', 'foo')
            }
        }
    }

    def 'adds an action with short lived token and password'() {
        given:
        def config = InjectionConfig.get()
        config.setServer('http://localhost')

        def accessKeyCredentialId = UUID.randomUUID().toString()
        def repositoryPasswordCredentialId = UUID.randomUUID().toString()
        def accessKey = "localhost=secret"
        config.setEnabled(true)
        config.setAccessKeyCredentialId(accessKeyCredentialId)
        config.setGradlePluginRepositoryCredentialId(repositoryPasswordCredentialId)
        config.save()

        createStringCredentials(accessKeyCredentialId, accessKey)
        createUsernamePasswordCredentials(repositoryPasswordCredentialId, "john", "foo")

        def key = DevelocityAccessCredentials.parse(accessKey).find('localhost').get()

        shortLivedTokenClient.get(config.getServer(), key, null) >> Optional.of(DevelocityAccessCredentials.HostnameAccessKey.of('localhost', 'xyz'))

        when:
        buildScanEnvironmentContributor.buildEnvironmentFor(run, new EnvVars(), TaskListener.NULL)

        then:
        3 * run.addAction { action ->
            if (action instanceof DevelocityParametersAction) {
                def parameters = action.getAllParameters()
                parameters.size() == 3
                paramEquals(parameters[0], 'GRADLE_ENTERPRISE_ACCESS_KEY', 'localhost=xyz')
                paramEquals(parameters[1], 'DEVELOCITY_ACCESS_KEY', 'localhost=xyz')
                paramEquals(parameters[2], 'DEVELOCITY_INJECTION_PLUGIN_REPOSITORY_PASSWORD', 'foo')
            }
        }
    }

    private static createUsernamePasswordCredentials(String id, String username, String password) {
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, id, "Username and Password credentials", username, password
                ))

        SystemCredentialsProvider.getInstance().save()
    }

    private static createStringCredentials(String id, String secret) {
        SystemCredentialsProvider.getInstance().getCredentials().add(
                new StringCredentialsImpl(
                        CredentialsScope.GLOBAL, id, "String credentials", Secret.fromString(secret)
                ))

        SystemCredentialsProvider.getInstance().save()
    }

    private static boolean paramEquals(ParameterValue param, String name, String value) {
        param.name == name && param.value?.plainText == value
    }

}
