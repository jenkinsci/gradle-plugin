package hudson.plugins.gradle

import hudson.model.FreeStyleProject
import hudson.plugins.gradle.injection.MavenSnippets
import hudson.plugins.timestamper.TimestamperBuildWrapper
import hudson.tasks.BatchFile
import hudson.tasks.Maven
import hudson.tasks.Shell
import org.apache.commons.lang3.SystemUtils
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jvnet.hudson.test.CreateFileBuilder
import org.jvnet.hudson.test.ExtractResourceSCM
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.ToolInstallations
import spock.lang.Requires
import spock.lang.Unroll

@Unroll
@SuppressWarnings("GStringExpressionWithinString")
class BuildScanIntegrationTest extends BaseGradleIntegrationTest {
    @Requires(value = { hasJdk8() }, reason = "Gradle 3 and 4 require Java 8")
    def 'build scans for plugin version #buildScanVersion is discovered'() {
        given:
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()
        setJdk8()
        FreeStyleProject p = j.createFreeStyleProject()
        p.buildersList.add(buildScriptBuilder(buildScanVersion))
        p.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "${args} --no-daemon"))
        p.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "${args} --no-daemon"))

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        println JenkinsRule.getLog(build)
        def action = build.getAction(BuildScanAction)
        action.scanUrls.size() == 2
        action.scanUrls.each { new URL(it) }

        where:
        buildScanVersion | gradleVersion | args
        '1.6'            | '3.4'         | '-Dscan'
        '1.8'            | '4.0'         | '--scan'
    }

    def 'build scans are discovered when timestamper is used'() {
        given:
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = j.createFreeStyleProject()
        p.buildersList.add(settingsScriptBuilder())
        p.buildersList.add(buildScriptBuilder())
        p.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleInstallationRule.gradleVersion, switches: '--scan --no-daemon'))
        p.getBuildWrappersList().add(new TimestamperBuildWrapper())

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        println JenkinsRule.getLog(build)
        def action = build.getAction(BuildScanAction)
        action.scanUrls.size() == 1
        action.scanUrls.each { new URL(it) }
    }

    def 'build scan is discovered when using non-gradle build step'() {
        given:
        FreeStyleProject p = j.createFreeStyleProject()
        p.setScm(new ExtractResourceSCM(this.class.getResource('/gradle/wrapper.zip')))
        p.buildWrappersList.add(new BuildScanBuildWrapper())
        p.buildersList.add(buildScriptBuilder())
        p.buildersList.add(settingsScriptBuilder())
        p.buildersList.add(SystemUtils.IS_OS_UNIX ? new Shell('./gradlew --scan --no-daemon hello') : new BatchFile('gradlew.bat --scan --no-daemon hello'))

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        println JenkinsRule.getLog(build)
        def action = build.getAction(BuildScanAction)
        action.scanUrls.size() == 1
        new URL(action.scanUrls.get(0))
    }

    def 'detects build scan in pipeline log'() {
        given:
        gradleInstallationRule.addInstallation()
        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition("""
node {
   stage('Build') {
      // Run the maven build
      def gradleHome = tool name: '${gradleInstallationRule.gradleVersion}', type: 'gradle'
      writeFile file: 'settings.gradle', text: ''
      writeFile file: 'build.gradle', text: "buildScan { termsOfServiceUrl = 'https://gradle.com/terms-of-service'; termsOfServiceAgree = 'yes' }"
      if (isUnix()) {
         sh "'\${gradleHome}/bin/gradle' help --scan --no-daemon"
      } else {
         bat(/"\${gradleHome}\\bin\\gradle.bat" help --scan --no-daemon/)
      }
   }
   stage('Final') {
       def scans = findBuildScans()
       assert scans.size() == 1
   }
}
""", false))

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        println JenkinsRule.getLog(build)
        def action = build.getAction(BuildScanAction)
        action.scanUrls.size() == 1
        new URL(action.scanUrls.get(0))
    }

    def 'detects build scan in pipeline log using withGradle'() {
        given:
        gradleInstallationRule.addInstallation()
        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition("""
    def scans = []
    stage('Build') {
      def stepToExecuteInParallel = {
        node {
          // Run the maven build
          scans.addAll(withGradle {
            def gradleHome = tool name: '${gradleInstallationRule.gradleVersion}', type: 'gradle'
            writeFile file: 'settings.gradle', text: ''
            writeFile file: 'build.gradle', text: "buildScan { termsOfServiceUrl = 'https://gradle.com/terms-of-service'; termsOfServiceAgree = 'yes' }"
            if (isUnix()) {
              sh "'\${gradleHome}/bin/gradle' help --scan --no-daemon"
            } else {
              bat(/"\${gradleHome}\\bin\\gradle.bat" help --scan --no-daemon/)
            }
          })
        }
      }
      parallel first: stepToExecuteInParallel, second: stepToExecuteInParallel
    }
    stage('Final') {
      assert scans.size() == 2
    }
""", false))

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        println JenkinsRule.getLog(build)
        def action = build.getAction(BuildScanAction)
        action.scanUrls.size() == 2
        new URL(action.scanUrls.get(0))
        new URL(action.scanUrls.get(1))
    }

    def 'does not find build scans in pipeline logs when none have been published'() {
        given:
        gradleInstallationRule.addInstallation()
        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition("""
node {
   stage('Build') {
      def gradleHome = tool name: '${gradleInstallationRule.gradleVersion}', type: 'gradle'
      writeFile file: 'settings.gradle', text: ''
      if (isUnix()) {
         sh "'\${gradleHome}/bin/gradle' help --no-scan --no-daemon"
      } else {
         bat(/"\${gradleHome}\\bin\\gradle.bat" help --no-scan --no-daemon/)
      }
   }
   stage('Final') {
       def scans = findBuildScans()
       assert scans.size() == 0
   }
}
""", false))

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        println JenkinsRule.getLog(build)
    }

    def 'does not find build scans in pipeline logs when none have been published with withGradle'() {
        given:
        gradleInstallationRule.addInstallation()
        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition("""
def scans = []
stage('Build') {
  node {
    // Run the maven build
    def gradleHome = tool name: '${gradleInstallationRule.gradleVersion}', type: 'gradle'
    writeFile file: 'settings.gradle', text: ''
    scans.addAll(withGradle {
      if (isUnix()) {
        sh "'\${gradleHome}/bin/gradle' help --no-scan --no-daemon"
      } else {
        bat(/"\${gradleHome}\\bin\\gradle.bat" help --no-scan --no-daemon/)
      }
    })
  }
}
stage('Final') {
  assert scans.size() == 0
}
""", false))

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        println JenkinsRule.getLog(build)
    }

    def 'build scan is discovered from Maven build'() {
        given:
        def p = j.createFreeStyleProject()
        p.buildWrappersList.add(new BuildScanBuildWrapper())
        p.buildersList.add(new CreateFileBuilder('pom.xml', MavenSnippets.simplePom()))
        p.buildersList.add(new CreateFileBuilder('.mvn/extensions.xml', MavenSnippets.buildScanExtensions()))
        p.buildersList.add(new CreateFileBuilder('.mvn/develocity.xml', MavenSnippets.develocityConfiguration()))
        def mavenInstallation = ToolInstallations.configureMaven35()
        p.buildersList.add(new Maven('package', mavenInstallation.name, null, '', '', false, null, null))

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        println JenkinsRule.getLog(build)
        def action = build.getAction(BuildScanAction)
        action.scanUrls.size() == 1
        new URL(action.scanUrls.get(0))
    }

    def 'build scan action is exposed via rest API'() {
        given:
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = j.createFreeStyleProject()
        p.buildersList.add(settingsScriptBuilder())
        p.buildersList.add(buildScriptBuilder())
        p.buildersList.add(new Gradle(tasks: 'hello', gradleName: "${gradleInstallationRule.gradleVersion}", switches: '-Dscan --no-daemon'))

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        println JenkinsRule.getLog(build)

        def json = j.getJSON("${build.url}/api/json?tree=actions[*]")
        def scanUrls = json.getJSONObject().get('actions').get(1).get('scanUrls')
        scanUrls.size() == 1
        new URL(scanUrls.get(0))
    }

    private static CreateFileBuilder buildScriptBuilder(String buildScanVersion = '4.0.1') {
        if (buildScanVersion.startsWith('4')) {
            return new CreateFileBuilder('build.gradle', """
            develocity {
                buildScan {
                    termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
                    termsOfUseAgree = "yes"
                }
                task hello { doLast { println 'Hello' } }
            }""")
        } else {
            new CreateFileBuilder('build.gradle', """
            buildScan {
                termsOfServiceUrl = 'https://gradle.com/terms-of-service'
                termsOfServiceAgree = 'yes'
            }
            task hello { doLast { println 'Hello' } }""")
        }
    }

    private static CreateFileBuilder settingsScriptBuilder(String pluginVersion = '4.0.1') {
        return pluginVersion.startsWith('4') ? new CreateFileBuilder('settings.gradle', "plugins { id 'com.gradle.develocity' version '${pluginVersion}' }") :
            new CreateFileBuilder('settings.gradle', "plugins { id 'com.gradle.entreprise' version '${pluginVersion}' }")
    }

}
