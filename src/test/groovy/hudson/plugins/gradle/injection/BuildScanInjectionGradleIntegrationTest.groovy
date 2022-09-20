package hudson.plugins.gradle.injection

import hudson.EnvVars
import hudson.model.FreeStyleProject
import hudson.plugins.gradle.Gradle
import hudson.slaves.DumbSlave
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.slaves.NodeProperty
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

    def 'init script is deleted without JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION set'(String gradleVersion) {
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
        withAdditionalGlobalEnvVars { put(GradleBuildScanInjection.FEATURE_TOGGLE_DISABLED_NODES, 'bar,foo') }
        restartSlave(slave)

        then:
        !initScript.exists()

        when:
        withAdditionalGlobalEnvVars {
            put(GradleBuildScanInjection.FEATURE_TOGGLE_DISABLED_NODES, '')
            put(GradleBuildScanInjection.FEATURE_TOGGLE_ENABLED_NODES, 'daz,foo')
        }
        restartSlave(slave)

        then:
        initScript.exists()

        when:
        withAdditionalGlobalEnvVars {
            put(GradleBuildScanInjection.FEATURE_TOGGLE_DISABLED_NODES, '')
            put(GradleBuildScanInjection.FEATURE_TOGGLE_ENABLED_NODES, 'daz')
        }
        restartSlave(slave)

        then:
        !initScript.exists()

        where:
        gradleVersion << GRADLE_VERSIONS
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
        withGlobalEnvVars {
            put('JENKINSGRADLEPLUGIN_CCUD_PLUGIN_VERSION', '1.7')
            if (setGeUrl) {
                put('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL', 'http://foo.com')
            }
        }

        return createSlave('foo')
    }

    private static String getGradleHome(DumbSlave slave, String gradleVersion) {
        return "${slave.getRemoteFS()}/tools/hudson.plugins.gradle.GradleInstallation/${gradleVersion}"
    }

    private void enableBuildInjection(DumbSlave slave, String gradleVersion, URI repositoryAddress = null) {
        withAdditionalGlobalEnvVars {
            put('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_INJECTION', 'on')
            put("JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME", getGradleHome(slave, gradleVersion))
            put('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION', '3.10.1')
            put('GRADLE_OPTS', '-Dscan.uploadInBackground=false')
            if (repositoryAddress != null) {
                put('JENKINSGRADLEPLUGIN_GRADLE_PLUGIN_REPOSITORY_URL', repositoryAddress.toASCIIString())
            }
        }
        restartSlave(slave)
    }

    private void disableBuildInjection(DumbSlave slave, String gradleVersion) {
        NodeProperty nodeProperty = new EnvironmentVariablesNodeProperty()
        EnvVars env = nodeProperty.getEnvVars()

        env.remove('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_INJECTION')
        env.put("JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME", getGradleHome(slave, gradleVersion))

        j.jenkins.globalNodeProperties.clear()
        j.jenkins.globalNodeProperties.add(nodeProperty)

        // sync changes
        restartSlave(slave)
    }

    private void turnOffBuildInjection(DumbSlave slave, String gradleVersion) {
        NodeProperty nodeProperty = new EnvironmentVariablesNodeProperty()
        EnvVars env = nodeProperty.getEnvVars()

        env.put('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_INJECTION', 'on')
        env.remove('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION')
        env.put("JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME", getGradleHome(slave, gradleVersion))

        j.jenkins.globalNodeProperties.clear()
        j.jenkins.globalNodeProperties.add(nodeProperty)

        // sync changes
        restartSlave(slave)
    }
}
