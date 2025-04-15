package hudson.plugins.gradle

import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.domains.Domain
import hudson.Functions
import hudson.slaves.DumbSlave
import hudson.util.Secret
import org.jenkinsci.plugins.plaincredentials.StringCredentials
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl
import org.junit.Rule
import org.junit.rules.RuleChain

import java.util.concurrent.TimeUnit

/**
 * Base class for tests that need a Jenkins instance and Gradle tool.
 */
abstract class BaseGradleIntegrationTest extends AbstractIntegrationTest {

    public final GradleInstallationRule gradleInstallationRule = new GradleInstallationRule(j)
    static final String DEVELOCITY_PLUGIN_VERSION = '3.17.1'
    static final String CCUD_PLUGIN_VERSION = '2.0'

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
                                      Boolean globalAutoInjectionCheckEnabled = false,
                                      boolean captureTaskInputFiles = false
    ) {
        withGlobalEnvVars {
            put("JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME", getGradleHome(slave, gradleVersion))
            put('GRADLE_OPTS', '-Ddevelocity.build-scan.upload-in-background=false')
            if (globalAutoInjectionCheckEnabled) {
                put("JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK", "true")
            }
        }

        withInjectionConfig {
            enabled = true
            gradlePluginVersion = DEVELOCITY_PLUGIN_VERSION
            ccudPluginVersion = CCUD_PLUGIN_VERSION
            gradlePluginRepositoryUrl = repositoryAddress?.toString()
            gradleCaptureTaskInputFiles = captureTaskInputFiles
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

    @Override
    @SuppressWarnings("CatchException")
    void cleanup() {
        if(Functions.isWindows()) {
            try {
                println 'Killing Gradle processes'
                def proc = '''WMIC PROCESS where "Name like 'java%' AND CommandLine like '%hudson.plugins.gradle.GradleInstallation%'" Call Terminate"'''.execute()
                proc.waitFor(30, TimeUnit.SECONDS)
                println "output: ${proc.text}"
                println "code: ${proc.exitValue()}"
            } catch (Exception e) {
                System.err.println('Failed killing Gradle daemons')
                e.printStackTrace()
            }
        }
    }

}
