package hudson.plugins.gradle.injection

import hudson.Util
import hudson.model.FreeStyleProject
import hudson.plugins.gradle.Gradle
import hudson.slaves.DumbSlave
import org.apache.commons.lang3.StringUtils
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jvnet.hudson.test.CreateFileBuilder
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Unroll

@Unroll
class BuildScanInjectionGradleIntegrationTest extends BaseGradleInjectionIntegrationTest {

    private static final String MSG_INIT_SCRIPT_APPLIED = "Connection to Gradle Enterprise: http://foo.com"

    private static final List<String> GRADLE_VERSIONS = ['4.10.3', '5.6.4', '6.9.2', '7.5.1']

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

        project.buildersList.add(buildScriptBuilder())
        project.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "--no-daemon"))

        when:
        // first build to download Gradle
        def firstRun = j.buildAndAssertSuccess(project)

        then:
        println JenkinsRule.getLog(firstRun)
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, firstRun)

        when:
        enableBuildInjection(agent, gradleVersion, proxyAddress)
        def secondRun = j.buildAndAssertSuccess(project)

        then:
        println JenkinsRule.getLog(secondRun)
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

        p.buildersList.add(buildScriptBuilder())
        p.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "--no-daemon"))

        when:
        // first build to download Gradle
        def build = j.buildAndAssertSuccess(p)

        then:
        println JenkinsRule.getLog(build)
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, build)

        when:
        enableBuildInjection(slave, gradleVersion)

        then:
        def initScript = new File(getGradleHome(slave, gradleVersion) + "/init.d/init-build-scan.gradle")
        !initScript.exists()

        when:
        def build2 = j.buildAndAssertSuccess(p)

        then:
        println JenkinsRule.getLog(build2)
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

        p.buildersList.add(buildScriptBuilder())
        p.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "--no-daemon"))

        when:
        // first build to download Gradle
        def build = j.buildAndAssertSuccess(p)

        then:
        println JenkinsRule.getLog(build)
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, build)

        when:
        enableBuildInjection(slave, gradleVersion)
        def build2 = j.buildAndAssertSuccess(p)

        then:
        println JenkinsRule.getLog(build2)
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
        println JenkinsRule.getLog(build)
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, build)

        when:
        enableBuildInjection(slave, gradleVersion)
        def build2 = j.buildAndAssertSuccess(pipelineJob)

        then:
        println JenkinsRule.getLog(build2)
        j.assertLogContains(MSG_INIT_SCRIPT_APPLIED, build2)

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    def 'init script is deleted without gradle plugin version set'(String gradleVersion) {
        given:
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave slave = createSlave()

        File initScript = new File(getGradleHome(slave, gradleVersion) + "/init.d/init-build-scan.gradle")

        expect:
        !initScript.exists()

        when:
        enableBuildInjection(slave, gradleVersion)

        then:
        initScript.exists()

        when:
        disableBuildInjection(slave, gradleVersion)

        then:
        initScript.exists()

        when:
        turnOffBuildInjection(slave, gradleVersion)

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

        File initScript = new File(getGradleHome(slave, gradleVersion) + "/init.d/init-build-scan.gradle")

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

        then:
        !initScript.exists()

        when:
        withInjectionConfig {
            gradleInjectionDisabledNodes = null
            gradleInjectionEnabledNodes = labels('daz', 'foo')
        }
        restartSlave(slave)

        then:
        initScript.exists()

        when:
        withInjectionConfig {
            gradleInjectionDisabledNodes = null
            gradleInjectionEnabledNodes = labels('daz')
        }
        restartSlave(slave)

        then:
        !initScript.exists()

        where:
        gradleVersion << GRADLE_VERSIONS
    }

    def "doesn't copy init script if already exists"() {
        given:
        def gradleVersion = '7.5.1'

        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave()

        def initScript = new File(getGradleHome(agent, gradleVersion) + "/init.d/init-build-scan.gradle")

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

        def initScript = new File(getGradleHome(agent, gradleVersion) + "/init.d/init-build-scan.gradle")

        when:
        enableBuildInjection(agent, gradleVersion, URI.create("https://my-company.com/m2/"))

        then:
        initScript.exists()
        def firstLastModified = initScript.lastModified()
        firstLastModified > 0
        def firstDigest = Util.getDigestOf(initScript)
        firstDigest != null

        when:
        initScript << "\n// comment"

        then:
        def secondLastModified = initScript.lastModified()
        secondLastModified != firstLastModified
        def secondDigest = Util.getDigestOf(initScript)
        secondDigest != firstDigest

        when:
        enableBuildInjection(agent, gradleVersion)

        then:
        initScript.exists()
        def thirdLastModified = initScript.lastModified()
        thirdLastModified != firstLastModified
        thirdLastModified != secondLastModified
        def thirdDigest = Util.getDigestOf(initScript)
        thirdDigest != secondDigest
        thirdDigest == firstDigest
    }

    private static CreateFileBuilder buildScriptBuilder() {
        return new CreateFileBuilder('build.gradle', """
task hello {
  doLast {
    println 'Hello!'
  }
}
""")
    }

    private DumbSlave createSlave(boolean setGeUrl = true) {
        withInjectionConfig {
            ccudPluginVersion = '1.7'
            server = setGeUrl ? 'http://foo.com' : null
        }

        return createSlave('foo')
    }

    private static String getGradleHome(DumbSlave slave, String gradleVersion) {
        return "${slave.getRemoteFS()}/tools/hudson.plugins.gradle.GradleInstallation/${gradleVersion}"
    }

    private void enableBuildInjection(DumbSlave slave, String gradleVersion, URI repositoryAddress = null) {
        withGlobalEnvVars {
            put("JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME", getGradleHome(slave, gradleVersion))
            put('GRADLE_OPTS', '-Dscan.uploadInBackground=false')
        }

        withInjectionConfig {
            enabled = true
            gradlePluginVersion = '3.10.1'
            gradlePluginRepositoryUrl = repositoryAddress?.toString()
        }

        restartSlave(slave)
    }

    private void disableBuildInjection(DumbSlave slave, String gradleVersion) {
        withGlobalEnvVars {
            put("JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME", getGradleHome(slave, gradleVersion))
        }

        withInjectionConfig {
            enabled = false
        }

        // sync changes
        restartSlave(slave)
    }

    private void turnOffBuildInjection(DumbSlave slave, String gradleVersion) {
        withGlobalEnvVars {
            put("JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME", getGradleHome(slave, gradleVersion))
        }

        withInjectionConfig {
            enabled = true
            gradlePluginVersion = null
        }

        // sync changes
        restartSlave(slave)
    }
}
