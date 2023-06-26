package hudson.plugins.gradle

import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.domains.Domain
import hudson.slaves.DumbSlave
import hudson.util.Secret
import org.jenkinsci.plugins.plaincredentials.StringCredentials
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl
import org.junit.Rule
import org.junit.rules.RuleChain

/**
 * Base class for tests that need a Jenkins instance and Gradle tool.
 */
abstract class BaseGradleIntegrationTest extends AbstractIntegrationTest {

    public final GradleInstallationRule gradleInstallationRule = new GradleInstallationRule(j)
    static final String GRADLE_ENTERPRISE_PLUGIN_VERSION = '3.13.4'

    @Rule
    public final RuleChain rules = RuleChain.outerRule(noSpaceInTmpDirs).around(j).around(gradleInstallationRule)

    Map getDefaults() {
        [
            gradleName        : gradleInstallationRule.gradleVersion,
            useWorkspaceAsHome: true,
            switches          : '--no-daemon'
        ]
    }

    def enableBuildInjection(DumbSlave slave,
                                      String gradleVersion,
                                      URI repositoryAddress = null,
                                      Boolean globalAutoInjectionCheckEnabled = false) {
        withGlobalEnvVars {
            put("JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME", getGradleHome(slave, gradleVersion))
            put('GRADLE_OPTS', '-Dscan.uploadInBackground=false')
            if (globalAutoInjectionCheckEnabled) {
                put("JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK", "true")
            }
        }

        withInjectionConfig {
            enabled = true
            gradlePluginVersion = GRADLE_ENTERPRISE_PLUGIN_VERSION
            gradlePluginRepositoryUrl = repositoryAddress?.toString()
        }

        restartSlave(slave)
    }

    static String getGradleHome(DumbSlave slave, String gradleVersion) {
        return "${slave.getRemoteFS()}/tools/hudson.plugins.gradle.GradleInstallation/${gradleVersion}"
    }

    def registerCredentials(String id, String secret) {
        StringCredentials creds = new StringCredentialsImpl(CredentialsScope.GLOBAL, id, null, Secret.fromString(secret))
        CredentialsProvider.lookupStores(j.jenkins).iterator().next().addCredentials(Domain.global(), creds)
    }

}
