package hudson.plugins.gradle

import hudson.plugins.timestamper.TimestamperConfig
import hudson.slaves.DumbSlave
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jvnet.hudson.test.JenkinsRule

class GradleConsoleAnnotatorIntegrationTest extends BaseGradleIntegrationTest {

    def 'pipeline job has Gradle tasks annotated'(boolean enableTimestamper) {
        given:
        def gradleVersion = '8.1.1'
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave('foo')
        def pipelineJob = j.createProject(WorkflowJob)

        pipelineJob.setDefinition(new CpsFlowDefinition("""
    stage('Build') {
      node('foo') {
        withGradle {
          git branch: 'main', url: 'https://github.com/c00ler/simple-gradle-project'
          def gradleHome = tool name: '${gradleInstallationRule.gradleVersion}', type: 'gradle'
          if (isUnix()) {
            sh "'\${gradleHome}/bin/gradle' clean build --no-daemon --console=plain"
          } else {
            bat(/"\${gradleHome}\\bin\\gradle.bat" clean build --no-daemon --console=plain/)
          }
        }
      }
    }
""", false))

        if (enableTimestamper) {
            TimestamperConfig config = TimestamperConfig.get()
            config.setAllPipelines(true)
            config.save()
        }

        when:
        def b = j.buildAndAssertSuccess(pipelineJob)

        then:
        println "logs: \n${JenkinsRule.getLog(b)}"
        def client = j.createWebClient()
        def html = client.goTo(b.getUrl() + "console")
        html.getByXPath("//b[@class='gradle-task']")*.textContent*.toString() == [
            ' Task :clean',
            ' Task :compileJava',
            ' Task :processResources',
            ' Task :classes',
            ' Task :jar',
            ' Task :assemble',
            ' Task :compileTestJava',
            ' Task :processTestResources',
            ' Task :testClasses',
            ' Task :test',
            ' Task :check',
            ' Task :build'
        ]
        html.getByXPath("//span[@class='gradle-task-progress-status']")*.textContent*.toString() == [
            'UP-TO-DATE\n',
            'NO-SOURCE\n',
            'NO-SOURCE\n'
        ]
        html.getByXPath("//span[@class='gradle-outcome-success']")*.textContent*.toString() == ['BUILD SUCCESSFUL']

        where:
        enableTimestamper << [ true, false ]
    }

}
