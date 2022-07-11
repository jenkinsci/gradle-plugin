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

  private static final String MSG_INIT_SCRIPT_APPLIED = "Connection to Gradle Enterprise: http://foo.com"

  def 'Gradle #gradleVersion - manual step - conditional build scan publication'() {
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
    gradleVersion << ['4.10.3', '5.6.4', '6.9.2', '7.4.2']
  }

  def 'Gradle #gradleVersion - pipeline - conditional build scan publication'() {
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
    gradleVersion << ['4.10.3', '5.6.4', '6.9.2', '7.4.2']
  }

  def 'init script is deleted without JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION set'() {
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
    gradleVersion << ['7.4.2']
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

  private DumbSlave createSlave() {
    NodeProperty nodeProperty = new EnvironmentVariablesNodeProperty()
    EnvVars env = nodeProperty.getEnvVars()

    env.put('JENKINSGRADLEPLUGIN_CCUD_PLUGIN_VERSION', '1.7')
    env.put('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL', 'http://foo.com')

    DumbSlave slave = j.createOnlineSlave(Label.get("foo"), env)

    return slave
  }

  private String getGradleHome(DumbSlave slave, String gradleVersion) {
    return slave.getRemoteFS() + "/tools/hudson.plugins.gradle.GradleInstallation/" + gradleVersion
  }

  private void enableBuildInjection(DumbSlave slave, String gradleVersion) {
    NodeProperty nodeProperty = new EnvironmentVariablesNodeProperty()
    EnvVars env = nodeProperty.getEnvVars()

    // we override the location of the init script to a workspace internal folder to allow parallel test runs
    env.put('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_INJECTION','on')
    env.put("JENKINSGRADLEPLUGIN_BUILD_SCAN_OVERRIDE_GRADLE_HOME", getGradleHome(slave, gradleVersion))
    env.put('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION', '3.10.1')
    env.put('GRADLE_OPTS','-Dscan.uploadInBackground=false')

    j.jenkins.globalNodeProperties.add(nodeProperty)

    // sync changes
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

  private void restartSlave(DumbSlave slave) {
    j.disconnectSlave(slave)
    j.waitOnline(slave)
  }
}
