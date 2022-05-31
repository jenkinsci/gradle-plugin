package hudson.plugins.gradle.injection

import hudson.EnvVars
import hudson.model.FreeStyleProject
import hudson.model.Label
import hudson.plugins.gradle.AbstractIntegrationTest
import hudson.plugins.gradle.Gradle
import hudson.slaves.DumbSlave
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.slaves.NodeProperty
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jvnet.hudson.test.CreateFileBuilder
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Unroll

@Unroll
class BuildScanInjectionGradleIntegrationTest extends AbstractIntegrationTest {

  private static final String MSG_PUBLISH_BUILD_SCAN = "Publishing build scan..."

  def 'build scan is published without GE plugin with Gradle manual step #gradleVersion'() {
    given:
    gradleInstallationRule.gradleVersion = gradleVersion
    gradleInstallationRule.addInstallation()

    DumbSlave slave = setupBuildInjection(false)
    FreeStyleProject p = j.createFreeStyleProject()
    p.setAssignedNode(slave)

    p.buildersList.add(buildScriptBuilder())
    p.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "--no-daemon"))

    when:
    def build = j.buildAndAssertSuccess(p)

    then:
    println JenkinsRule.getLog(build)
    j.assertLogContains(MSG_PUBLISH_BUILD_SCAN, build)

    where:
    gradleVersion << ['4.10.3', '5.6.4', '6.9.2', '7.4.2']
  }

  def 'build scan is not published without JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION on manual step'() {
    given:
    gradleInstallationRule.gradleVersion = gradleVersion
    gradleInstallationRule.addInstallation()
    DumbSlave slave = j.createOnlineSlave()
    FreeStyleProject p = j.createFreeStyleProject()
    p.setAssignedNode(slave)

    p.buildersList.add(buildScriptBuilder())
    p.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "--no-daemon"))

    when:
    def build = j.buildAndAssertSuccess(p)

    then:
    println JenkinsRule.getLog(build)
    j.assertLogNotContains(MSG_PUBLISH_BUILD_SCAN, build)

    where:
    gradleVersion << ['7.4.2']
  }

  def 'build scan is published without GE plugin with Gradle pipeline #gradleVersion'() {
    given:
    gradleInstallationRule.gradleVersion = gradleVersion
    gradleInstallationRule.addInstallation()

    setupBuildInjection(false)
    def pipelineJob = j.createProject(WorkflowJob)

    pipelineJob.setDefinition(new CpsFlowDefinition("""
    stage('Build') {
      node {
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
    def build = j.buildAndAssertSuccess(pipelineJob)

    then:
    j.waitForCompletion(build)
    println JenkinsRule.getLog(build)
    j.assertLogContains(MSG_PUBLISH_BUILD_SCAN, build)

    where:
    gradleVersion << ['4.10.3', '5.6.4', '6.9.2', '7.4.2']
  }

  def 'build scan is not published without JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION on pipeline'() {
    given:
    gradleInstallationRule.gradleVersion = gradleVersion
    gradleInstallationRule.addInstallation()
    def pipelineJob = j.createProject(WorkflowJob)

    pipelineJob.setDefinition(new CpsFlowDefinition("""
    stage('Build') {
      node {
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
    def build = j.buildAndAssertSuccess(pipelineJob)

    then:
    println JenkinsRule.getLog(build)
    j.assertLogNotContains(MSG_PUBLISH_BUILD_SCAN, build)

    where:
    gradleVersion << ['7.4.2']
  }

  def 'init script is copied in a custom gradle home'() {
    given:
    gradleInstallationRule.gradleVersion = gradleVersion
    gradleInstallationRule.addInstallation()

    setupBuildInjection(true)
    def pipelineJob = j.createProject(WorkflowJob)

    pipelineJob.setDefinition(new CpsFlowDefinition("""
    stage('Build') {
      node {
        withEnv(['JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION=3.10.1','JENKINSGRADLEPLUGIN_CCUD_PLUGIN_VERSION=1.7','JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL=http://foo.com','JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_HOME=/tmp']){
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

          def exists = fileExists '/tmp/.gradle/init.d/init-build-scan.gradle'
          if (!exists) {
            error "Gradle init script not found"
          }
        }
      }
    }
""", false))

    when:
    def build = j.buildAndAssertSuccess(pipelineJob)

    then:
    println JenkinsRule.getLog(build)

    where:
    gradleVersion << ['7.4.2']
  }

  def 'init script is deleted without JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION'() {
    given:
    gradleInstallationRule.gradleVersion = gradleVersion
    gradleInstallationRule.addInstallation()

    NodeProperty nodeProperty = new EnvironmentVariablesNodeProperty()
    EnvVars env = nodeProperty.getEnvVars()
    env.put('JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_HOME','/tmp')
    j.jenkins.globalNodeProperties.add(nodeProperty)
    j.createOnlineSlave(Label.get("foo"), env)
    def pipelineJob = j.createProject(WorkflowJob)

    pipelineJob.setDefinition(new CpsFlowDefinition("""
    stage('Build') {
      node {
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

        def exists = fileExists '/tmp/.gradle/init.d/init-build-scan.gradle'
        if (exists) {
          error "Gradle init script not deleted"
        }
      }
    }
""", false))

    when:
    def build = j.buildAndAssertSuccess(pipelineJob)

    then:
    println JenkinsRule.getLog(build)

    where:
    gradleVersion << ['7.4.2']
  }

  private static CreateFileBuilder buildScriptBuilder() {
    return new CreateFileBuilder('build.gradle', """
task hello { doLast { println 'Hello' } }""")
  }

  private static boolean isUnix() {
    return File.pathSeparatorChar == ':' as char
  }

  private DumbSlave setupBuildInjection(boolean withCustomGradleHome) {
    NodeProperty nodeProperty = new EnvironmentVariablesNodeProperty()
    EnvVars env = nodeProperty.getEnvVars()
    env.put('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION', '3.10.1')
    env.put('JENKINSGRADLEPLUGIN_CCUD_PLUGIN_VERSION', '1.7')
    env.put('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL', 'http://foo.com')
    if(withCustomGradleHome){
      env.put('JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_HOME','/tmp')
    }
    j.jenkins.globalNodeProperties.add(nodeProperty)
    DumbSlave slave = j.createOnlineSlave(Label.get("foo"), env)
    slave
  }

}
