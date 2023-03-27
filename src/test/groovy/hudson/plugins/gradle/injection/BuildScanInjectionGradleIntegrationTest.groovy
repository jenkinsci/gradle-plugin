package hudson.plugins.gradle.injection

import hudson.EnvVars
import hudson.Util
import hudson.model.FreeStyleProject
import hudson.model.Slave
import hudson.plugins.gradle.BaseGradleIntegrationTest
import hudson.plugins.gradle.Gradle
import hudson.slaves.DumbSlave
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.util.Secret
import org.apache.commons.lang3.StringUtils
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jvnet.hudson.test.CreateFileBuilder
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Unroll

@Unroll
class BuildScanInjectionGradleIntegrationTest extends BaseGradleIntegrationTest {

    private static final String GRADLE_ENTERPRISE_PLUGIN_VERSION = '3.11.1'
    private static final String CCUD_PLUGIN_VERSION = '1.8.1'

    private static final String MSG_INIT_SCRIPT_APPLIED = "Connection to Gradle Enterprise: http://foo.com"

    private static final List<String> GRADLE_VERSIONS = ['4.10.3', '5.6.4', '6.9.4', '7.6.1', '8.0.2']

    private static final EnvVars EMPTY_ENV = new EnvVars()

    def 'uses custom plugin repository'() {
        given:
        // Gradle 7.x requires allowInsecureProtocol
        def gradleVersion = '6.9.2'
        def pluginRepositoryUrl = URI.create("https://plugins.gradle.org/m2/")

        def proxy = new ExternalRepoProxy(pluginRepositoryUrl)
        def proxyAddress = proxy.address

        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave()

        FreeStyleProject project = j.createFreeStyleProject()
        project.setAssignedNode(agent)

        project.buildersList.add(helloTask())
        project.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "--no-daemon"))

        when:
        // first build to download Gradle
        def firstRun = j.buildAndAssertSuccess(project)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, firstRun)

        when:
        enableBuildInjection(agent, gradleVersion, proxyAddress)
        def secondRun = j.buildAndAssertSuccess(project)

        then:
        j.assertLogContains(MSG_INIT_SCRIPT_APPLIED, secondRun)
        j.assertLogContains(
            "Gradle Enterprise plugins resolution: ${StringUtils.removeEnd(proxyAddress.toString(), "/")}", secondRun)

        cleanup:
        proxy.close()
    }

    def 'skips the injection if GE url is not set'(String gradleVersion) {
        given:
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave slave = createSlave(false)

        FreeStyleProject p = j.createFreeStyleProject()
        p.setAssignedNode(slave)

        p.buildersList.add(helloTask())
        p.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "--no-daemon"))

        when:
        // first build to download Gradle
        def build = j.buildAndAssertSuccess(p)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, build)

        when:
        enableBuildInjection(slave, gradleVersion)

        then:
        !initScriptFile(slave, gradleVersion).exists()

        when:
        def build2 = j.buildAndAssertSuccess(p)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, build2)

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    def 'Gradle #gradleVersion - freestyle - conditional build scan publication'(String gradleVersion) {
        given:
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave slave = createSlave()

        FreeStyleProject p = j.createFreeStyleProject()
        p.setAssignedNode(slave)

        p.buildersList.add(helloTask())
        p.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "--no-daemon"))

        when:
        // first build to download Gradle
        def build = j.buildAndAssertSuccess(p)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, build)

        when:
        enableBuildInjection(slave, gradleVersion)
        def build2 = j.buildAndAssertSuccess(p)

        then:
        j.assertLogContains(MSG_INIT_SCRIPT_APPLIED, build2)

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    def 'Gradle #gradleVersion - pipeline - conditional build scan publication'(String gradleVersion) {
        given:
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave slave = createSlave()

        def pipelineJob = j.createProject(WorkflowJob)

        pipelineJob.setDefinition(new CpsFlowDefinition("""
    stage('Build') {
      node('foo') {
        withGradle {
          def gradleHome = tool name: '${gradleInstallationRule.gradleVersion}', type: 'gradle'
          writeFile file: 'settings.gradle', text: ''
          writeFile file: 'build.gradle', text: ""
          if (isUnix()) {
            sh "'\${gradleHome}/bin/gradle' help --no-daemon --console=plain"
          } else {
            bat(/"\${gradleHome}\\bin\\gradle.bat" help --no-daemon --console=plain/)
          }
        }
      }
    }
""", false))

        when:
        // first build to download Gradle
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, build)

        when:
        enableBuildInjection(slave, gradleVersion)
        def build2 = j.buildAndAssertSuccess(pipelineJob)

        then:
        j.assertLogContains(MSG_INIT_SCRIPT_APPLIED, build2)

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    def 'Gradle #gradleVersion - init script is deleted when auto-injection is turned off'(String gradleVersion) {
        given:
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave slave = createSlave()

        File initScript = initScriptFile(slave, gradleVersion)

        expect:
        !initScript.exists()

        when:
        enableBuildInjection(slave, gradleVersion)

        then:
        initScript.exists()

        when:
        disableBuildInjection(slave)

        then:
        !initScript.exists()

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    def 'Gradle #gradleVersion - init script is deleted without gradle plugin version set'(String gradleVersion) {
        given:
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave slave = createSlave()

        File initScript = initScriptFile(slave, gradleVersion)

        expect:
        !initScript.exists()

        when:
        enableBuildInjection(slave, gradleVersion, null, true)

        then:
        initScript.exists()

        when:
        disableBuildInjection(slave)

        then:
        initScript.exists()

        when:
        turnOffBuildInjection(slave)

        then:
        !initScript.exists()

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    def 'injection is enabled and disabled based on node labels'(String gradleVersion) {
        given:
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave slave = createSlave()

        File initScript = initScriptFile(slave, gradleVersion)

        FreeStyleProject p = j.createFreeStyleProject()
        p.setAssignedNode(slave)

        p.buildersList.add(helloTask())
        p.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "--no-daemon"))

        expect:
        !initScript.exists()

        when:
        enableBuildInjection(slave, gradleVersion)

        then:
        initScript.exists()

        when:
        withInjectionConfig {
            gradleInjectionDisabledNodes = labels('bar', 'foo')
        }
        restartSlave(slave)

        def firstBuild = j.buildAndAssertSuccess(p)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, firstBuild)

        when:
        withInjectionConfig {
            gradleInjectionDisabledNodes = null
            gradleInjectionEnabledNodes = labels('daz', 'foo')
        }
        restartSlave(slave)

        def secondBuild = j.buildAndAssertSuccess(p)

        then:
        j.assertLogContains(MSG_INIT_SCRIPT_APPLIED, secondBuild)

        when:
        withInjectionConfig {
            gradleInjectionDisabledNodes = null
            gradleInjectionEnabledNodes = labels('daz')
        }
        restartSlave(slave)

        def thirdBuild = j.buildAndAssertSuccess(p)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, thirdBuild)

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    def "doesn't copy init script if already exists"() {
        given:
        def gradleVersion = '7.5.1'

        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave()

        def initScript = initScriptFile(agent, gradleVersion)

        when:
        enableBuildInjection(agent, gradleVersion, URI.create("https://my-company.com/m2/"))

        then:
        initScript.exists()
        def firstLastModified = initScript.lastModified()
        firstLastModified > 0

        when:
        enableBuildInjection(agent, gradleVersion)

        then:
        initScript.exists()
        def secondLastModified = initScript.lastModified()
        firstLastModified == secondLastModified
    }

    def "copies init script if it was changed"() {
        given:
        def gradleVersion = '7.5.1'

        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave()

        def initScript = initScriptFile(agent, gradleVersion)

        when:
        enableBuildInjection(agent, gradleVersion, URI.create("https://my-company.com/m2/"))

        then:
        initScript.exists()
        def firstDigest = Util.getDigestOf(initScript)
        firstDigest != null

        when:
        initScript << "\n// comment"

        then:
        def secondDigest = Util.getDigestOf(initScript)
        secondDigest != firstDigest

        when:
        enableBuildInjection(agent, gradleVersion)

        then:
        initScript.exists()
        def thirdDigest = Util.getDigestOf(initScript)
        thirdDigest != secondDigest
        thirdDigest == firstDigest
    }

    @SuppressWarnings("GStringExpressionWithinString")
    def "access key is injected into the build"() {
        given:
        def gradleVersion = '8.0.1'

        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave()

        FreeStyleProject project = j.createFreeStyleProject()
        project.setAssignedNode(agent)

        project.buildersList.add(helloTask('println "accessKey=${System.getenv(\'GRADLE_ENTERPRISE_ACCESS_KEY\')}"'))
        project.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "--no-daemon"))

        when:
        // first build to download Gradle
        def firstRun = j.buildAndAssertSuccess(project)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, firstRun)

        when:
        enableBuildInjection(agent, gradleVersion)
        withInjectionConfig {
            accessKey = Secret.fromString("foo.com=secret")
        }
        def secondRun = j.buildAndAssertSuccess(project)

        then:
        j.assertLogContains(MSG_INIT_SCRIPT_APPLIED, secondRun)
        j.assertLogContains("accessKey=foo.com=secret", secondRun)
        j.assertLogContains("The response from http://foo.com/scans/publish/gradle/3.11.1/token was not from Gradle Enterprise.", secondRun)
        j.assertLogNotContains("ERROR: Gradle Enterprise access key format is not valid", secondRun)

    }

    def "invalid access key is not injected into the build"() {
        given:
        def gradleVersion = '8.0.1'

        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave()

        FreeStyleProject project = j.createFreeStyleProject()
        project.setAssignedNode(agent)

        project.buildersList.add(helloTask())
        project.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "--no-daemon"))

        when:
        // first build to download Gradle
        def firstRun = j.buildAndAssertSuccess(project)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, firstRun)

        when:
        enableBuildInjection(agent, gradleVersion)
        withInjectionConfig {
            accessKey = Secret.fromString("secret")
        }
        def secondRun = j.buildAndAssertSuccess(project)

        then:
        j.assertLogContains(MSG_INIT_SCRIPT_APPLIED, secondRun)
        j.assertLogContains("The response from http://foo.com/scans/publish/gradle/3.11.1/token was not from Gradle Enterprise.", secondRun)

        and:
        StringUtils.countMatches(JenkinsRule.getLog(secondRun), "ERROR: Gradle Enterprise access key format is not valid") == 1
    }

    private static CreateFileBuilder helloTask(String action = "println 'Hello!'") {
        return new CreateFileBuilder('build.gradle', """
task hello {
  doLast {
    $action
  }
}
""")
    }

    private DumbSlave createSlave(boolean setGeUrl = true) {
        withInjectionConfig {
            ccudPluginVersion = CCUD_PLUGIN_VERSION
            server = setGeUrl ? 'http://foo.com' : null
        }

        return createSlave('foo')
    }

    private static File initScriptFile(DumbSlave agent, String gradleVersion) {
        return new File("${getGradleHome(agent, gradleVersion)}/init.d/init-build-scan.gradle")
    }

    private static String getGradleHome(DumbSlave slave, String gradleVersion) {
        return "${slave.getRemoteFS()}/tools/hudson.plugins.gradle.GradleInstallation/${gradleVersion}"
    }

    private void enableBuildInjection(DumbSlave slave,
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

    private void disableBuildInjection(DumbSlave slave) {
        withInjectionConfig {
            enabled = false
        }

        restartSlave(slave)
    }

    private void turnOffBuildInjection(DumbSlave slave) {
        withInjectionConfig {
            enabled = true
            gradlePluginVersion = null
        }

        restartSlave(slave)
    }

    private static EnvVars agentEnvVars(Slave agent) {
        def nodeProperty = agent.getNodeProperties().get(EnvironmentVariablesNodeProperty.class)
        return nodeProperty != null ? nodeProperty.getEnvVars() : EMPTY_ENV
    }
}
