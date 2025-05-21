package hudson.plugins.gradle

import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.domains.Domain
import hudson.Functions
import hudson.model.JDK
import hudson.slaves.DumbSlave
import hudson.util.Secret
import org.jenkinsci.plugins.plaincredentials.StringCredentials
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl
import org.junit.Rule
import org.junit.rules.RuleChain
import org.jvnet.hudson.test.CreateFileBuilder

import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Base class for tests that need a Jenkins instance and Gradle tool.
 */
abstract class BaseGradleIntegrationTest extends AbstractIntegrationTest {

    public final GradleInstallationRule gradleInstallationRule = new GradleInstallationRule(j)
    static final String DEVELOCITY_PLUGIN_VERSION = '3.17.1'
    static final String CCUD_PLUGIN_VERSION = '2.0'
    private static final String JDK11_SYS_PROP = 'jdk11.home'
    private static final String JDK8_SYS_PROP = 'jdk8.home'

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
            put('GRADLE_OPTS', '-Ddevelocity-injection.upload-in-background=false')
            if (globalAutoInjectionCheckEnabled) {
                put("JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK", "true")
            }
        }

        withInjectionConfig {
            enabled = true
            gradlePluginVersion = DEVELOCITY_PLUGIN_VERSION
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
    def cleanup() {
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

    protected setJdk8() {
        j.jenkins.setJDKs(Collections.singleton(new JDK('JDK11', System.getProperty(JDK8_SYS_PROP))))
    }

    protected static hasJdk8() {
        def jdk8SysProp = System.getProperty(JDK8_SYS_PROP)
        return jdk8SysProp && Files.exists(Paths.get(jdk8SysProp))
    }

    protected setJdk11() {
        j.jenkins.setJDKs(Collections.singleton(new JDK('JDK11', System.getProperty(JDK11_SYS_PROP))))
    }

    protected static hasJdk11() {
        def jdk8SysProp = System.getProperty(JDK11_SYS_PROP)
        return jdk8SysProp && Files.exists(Paths.get(jdk8SysProp))
    }

    protected static CreateFileBuilder settingsFile() {
        new CreateFileBuilder('settings.gradle', '')
    }
}
