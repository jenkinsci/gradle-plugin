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
import spock.lang.Tag

import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Base class for tests that need a Jenkins instance and Gradle tool.
 */
@Tag('sequential')
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

    def cleanup() {
        killGradleDaemons()
    }

    def killGradleDaemons() {
        if (Functions.isWindows()) {
            try {
                println 'Killing Gradle processes'
                Files.write(
                    Paths.get('kill-gradle-processes.ps1'),
                    '''
                      $procs = Get-CimInstance Win32_Process -Filter "Name='java.exe' AND CommandLine LIKE '%GradleDaemon%'"
                      if ($procs) {
                          foreach ($p in $procs) {
                              $res = Invoke-CimMethod -InputObject $p -MethodName Terminate
                              Write-Output ("Terminated {0} -> ReturnValue {1}" -f $p.ProcessId, $res.ReturnValue)
                          }
                      } else {
                          Write-Output "No GradleDaemon java processes found."
                      }
                      exit 0
                    '''.stripIndent().trim().getBytes()
                )
                def proc =
                    ['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'kill-gradle-processes.ps1']
                        .execute()
                proc.waitFor(30, TimeUnit.SECONDS)
                println "code: ${proc.exitValue()}"
                println "stdout output: ${proc.in.text}"
                println "stderr output: ${proc.err.text}"
            } catch (Exception e) {
                System.err.println('Failed killing Gradle daemons')
                e.printStackTrace()
            }
        }
    }
}
