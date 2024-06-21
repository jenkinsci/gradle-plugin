package hudson.plugins.gradle

import hudson.model.FreeStyleProject
import hudson.model.JDK
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

import java.nio.file.Files
import java.nio.file.Paths

@Unroll
@SuppressWarnings("GStringExpressionWithinString")
class BuildScanIntegrationTest extends BaseGradleIntegrationTest {
    private static final String JDK8_SYS_PROP = 'jdk8.home'

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
        gradleInstallationRule.gradleVersion = '5.6'
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = j.createFreeStyleProject()
        p.buildersList.add(buildScriptKtsBuilder())
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
        p.buildersList.add(buildScriptBuilder('3.1.1'))
        p.buildersList.add(settingsScriptBuilder('3.1.1'))
        p.buildersList.add(SystemUtils.IS_OS_UNIX ? new Shell('./gradlew --scan hello') : new BatchFile('gradlew.bat --scan hello'))

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
      def gradleHome = tool name: '5.5', type: 'gradle'
      writeFile file: 'settings.gradle', text: ''
      writeFile file: 'build.gradle', text: "buildScan { termsOfServiceUrl = 'https://gradle.com/terms-of-service'; termsOfServiceAgree = 'yes' }"
      if (isUnix()) {
         sh "'\${gradleHome}/bin/gradle' help --scan"
      } else {
         bat(/"\${gradleHome}\\bin\\gradle.bat" help --scan/)
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
        gradleInstallationRule.gradleVersion = '5.6.4'
        gradleInstallationRule.addInstallation()
        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition("""
    def scans = []
    stage('Build') {
      node {
        def gradleHome = tool name: '${gradleInstallationRule.gradleVersion}', type: 'gradle'
        if (isUnix()) {
          sh "'\${gradleHome}/bin/gradle' --stop"
        } else {
          bat(/"\${gradleHome}\\bin\\gradle.bat" --stop/)
        }
      }
      def stepToExecuteInParallel = {
        node {
          // Run the maven build
          scans.addAll(withGradle {
            def gradleHome = tool name: '${gradleInstallationRule.gradleVersion}', type: 'gradle'
            writeFile file: 'settings.gradle', text: ''
            writeFile file: 'build.gradle', text: "buildScan { termsOfServiceUrl = 'https://gradle.com/terms-of-service'; termsOfServiceAgree = 'yes' }"
            if (isUnix()) {
              sh "'\${gradleHome}/bin/gradle' help --scan"
            } else {
              bat(/"\${gradleHome}\\bin\\gradle.bat" help --scan/)
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
        gradleInstallationRule.gradleVersion = '5.6.4'
        gradleInstallationRule.addInstallation()
        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition("""
node {
   stage('Build') {
      def gradleHome = tool name: '${gradleInstallationRule.gradleVersion}', type: 'gradle'
      writeFile file: 'settings.gradle', text: ''
      if (isUnix()) {
         sh "'\${gradleHome}/bin/gradle' --stop"
         sh "'\${gradleHome}/bin/gradle' help --no-scan"
      } else {
         bat(/"\${gradleHome}\\bin\\gradle.bat" --stop/)
         bat(/"\${gradleHome}\\bin\\gradle.bat" help --no-scan/)
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
        gradleInstallationRule.gradleVersion = '5.6.4'
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
        sh "'\${gradleHome}/bin/gradle' --stop"
        sh "'\${gradleHome}/bin/gradle' help --no-scan"
      } else {
        bat(/"\${gradleHome}\\bin\\gradle.bat" --stop/)
        bat(/"\${gradleHome}\\bin\\gradle.bat" help --no-scan/)
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
        p.buildersList.add(new CreateFileBuilder('.mvn/gradle-enterprise.xml', MavenSnippets.gradleEnterpriseConfiguration()))
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

    @Requires(value = { hasJdk8() }, reason = "Gradle 3 and 4 require Java 8")
    def 'build scan action is exposed via rest API'() {
        given:
        gradleInstallationRule.gradleVersion = '3.4'
        gradleInstallationRule.addInstallation()
        setJdk8()
        FreeStyleProject p = j.createFreeStyleProject()
        p.buildersList.add(buildScriptBuilder('1.8'))
        p.buildersList.add(new Gradle(tasks: 'hello', gradleName: '3.4', switches: '-Dscan --no-daemon'))

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        println JenkinsRule.getLog(build)

        def json = j.getJSON("${build.url}/api/json?tree=actions[*]")
        def scanUrls = json.getJSONObject().get('actions').get(1).get('scanUrls')
        scanUrls.size() == 1
        new URL(scanUrls.get(0))
    }

    private static CreateFileBuilder buildScriptBuilder(String buildScanVersion) {
        def plugins =
            buildScanVersion.startsWith('1') || buildScanVersion.startsWith('2')
                ? "plugins { id 'com.gradle.build-scan' version '${buildScanVersion}' }"
                : ''
        return new CreateFileBuilder('build.gradle', """
${plugins}

buildScan {
    ${buildScanVersion.startsWith('1') ? 'licenseAgreementUrl' : 'termsOfServiceUrl'} = 'https://gradle.com/terms-of-service'
    ${buildScanVersion.startsWith('1') ? 'licenseAgree' : 'termsOfServiceAgree'} = 'yes'
}

task hello { doLast { println 'Hello' } }""")
    }

    private static CreateFileBuilder buildScriptKtsBuilder() {
        return new CreateFileBuilder('build.gradle.kts', '''
plugins {
    `build-scan`
}

buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"
}

tasks.register("hello") { doLast { println("Hello") } }''')
    }

    private static CreateFileBuilder settingsScriptBuilder(String gePluginVersion) {
        return new CreateFileBuilder('settings.gradle', "plugins { id 'com.gradle.enterprise' version '${gePluginVersion}' }")
    }

    private setJdk8() {
        j.jenkins.setJDKs(Collections.singleton(new JDK('JDK8', System.getProperty(JDK8_SYS_PROP))))
    }

    private static hasJdk8() {
        def jdk8SysProp = System.getProperty(JDK8_SYS_PROP)
        return jdk8SysProp && Files.exists(Paths.get(jdk8SysProp))
    }

}
