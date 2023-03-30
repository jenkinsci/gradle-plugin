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

    def "maven opts and maven plugin environment variables are set if injection is enabled"() {
        given:
        withInjectionConfig {
            enabled = true
            server = 'https://scans.gradle.com'
            injectMavenExtension = true
            injectCcudExtension = true
            allowUntrusted = true
        }

        def mavenOpts = "-Dmaven.ext.class.path=/var/jenkins-gradle-plugin/lib/gradle-enterprise-maven-extension.jar:/var/jenkins-gradle-plugin/lib/common-custom-user-data-maven-extension.jar -Dgradle.scan.uploadInBackground=false -Dgradle.enterprise.url=https://scans.gradle.com -Dgradle.enterprise.allowUntrustedServer=true"
        def mavenPluginConfigExtClasspath = "/var/jenkins-gradle-plugin/lib/gradle-enterprise-maven-extension.jar:/var/jenkins-gradle-plugin/lib/common-custom-user-data-maven-extension.jar:/var/jenkins-gradle-plugin/lib/configuration-maven-extension.jar"
        def preparedMavenProperties = new MavenInjectionAware.NodePreparedMavenEnvsAction(mavenOpts, mavenPluginConfigExtClasspath)

        def mockRun = Mock(Run)
        mockRun.getAction(MavenInjectionAware.NodePreparedMavenEnvsAction.class) >> preparedMavenProperties

        when:
        mavenInjectionEnvironmentContributor.buildEnvironmentFor(mockRun, envs, TaskListener.NULL)

        then:
        with(envs.get(MavenOptsHandler.MAVEN_OPTS).split(" ").iterator()) {
            with(it.next()) {
                it.startsWith('-Dmaven.ext.class.path=')
                it.contains('gradle-enterprise-maven-extension.jar')
                it.contains('common-custom-user-data-maven-extension.jar')
            }
            with(it.next()) {
                it == '-Dgradle.scan.uploadInBackground=false'
            }
            with(it.next()) {
                it == '-Dgradle.enterprise.url=https://scans.gradle.com'
            }
            with(it.next()) {
                it == '-Dgradle.enterprise.allowUntrustedServer=true'
            }
            !it.hasNext()
        }
        with(envs.get(MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH)) {
            !it.startsWith('-Dmaven.ext.class.path=')
            it.contains('gradle-enterprise-maven-extension.jar')
            it.contains('common-custom-user-data-maven-extension.jar')
            it.contains('configuration-maven-extension.jar')
        }
        envs.get(MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL) == 'https://scans.gradle.com'
        envs.get(MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER) == 'true'
    }

}
