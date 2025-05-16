package hudson.plugins.gradle.injection

import hudson.plugins.gradle.GradleInstallationRule
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jvnet.hudson.test.JenkinsRule

class GradleSnippets {

    static WorkflowJob pipelineJobWithError(JenkinsRule j, GradleInstallationRule gradleInstallationRule) {
        def pipelineJob = j.createProject(WorkflowJob)

        pipelineJob.setDefinition(new CpsFlowDefinition("""
    stage('Build') {
      node('foo') {
        withGradle {
          def gradleHome = tool name: '${gradleInstallationRule.gradleVersion}', type: 'gradle'
          writeFile file: 'settings.gradle', text: ''
          writeFile file: 'build.gradle', text: ""
          if (isUnix()) {
            sh "'\${gradleHome}/bin/gradle' help --no-daemon --console=plain -Dcom.gradle.scan.trigger-synthetic-error=true -Ddevelocity.scan.trigger-synthetic-error=true"
          } else {
            bat(/"\${gradleHome}\\bin\\gradle.bat" help --no-daemon --console=plain -Dcom.gradle.scan.trigger-synthetic-error=true -Ddevelocity.scan.trigger-synthetic-error=true/)
          }
        }
      }
    }
""", false))
        return pipelineJob
    }

    static WorkflowJob pipelineJobWithCredentials(JenkinsRule j) {
        def pipelineJob = j.createProject(WorkflowJob)

        pipelineJob.setDefinition(new CpsFlowDefinition('''
    stage('Build') {
      node('foo') {
        withCredentials([string(credentialsId: 'my-creds', variable: 'PASSWORD')]) {
          if (isUnix()) {
            sh 'echo password=$PASSWORD'
          } else {
            bat "echo password=%PASSWORD%"
          }
        }
      }
    }
''', false))
        return pipelineJob
    }

}
