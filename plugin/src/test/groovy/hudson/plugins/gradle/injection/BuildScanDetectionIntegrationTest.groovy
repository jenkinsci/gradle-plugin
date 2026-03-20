package hudson.plugins.gradle.injection

import hudson.model.FreeStyleProject
import hudson.plugins.gradle.AbstractIntegrationTest
import hudson.plugins.gradle.BaseGradleIntegrationTest
import hudson.plugins.gradle.BuildScanAction
import hudson.tasks.Shell
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jvnet.hudson.test.CreateFileBuilder
import org.jvnet.hudson.test.ExtractResourceSCM
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Requires

@SuppressWarnings("GStringExpressionWithinString")
class BuildScanDetectionIntegrationTest extends BaseGradleIntegrationTest {

    @Requires(value = { os.linux || os.macOs }, reason = "Uses shell commands")
    def 'withGradle does not double-parse when global detection is enabled'() {
        given:
        withInjectionConfig {
            globalBuildScanDetection = true
        }
        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition("""
node {
    def scans = withGradle {
        sh '''echo "Publishing build scan..."
echo "https://scans.gradle.com/s/test123"'''
    }
    echo "WITHGRADLE_SCAN_COUNT=\${scans.size()}"
}
""", false))

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        def log = JenkinsRule.getLog(build)
        println log
        // Global detection should capture the scan
        def action = build.getAction(BuildScanAction)
        action != null
        action.scanUrls.size() == 1
        action.scanUrls.get(0) == 'https://scans.gradle.com/s/test123'
        // withGradle should NOT have detected scans (global handles it)
        log.contains('WITHGRADLE_SCAN_COUNT=0')
    }

    @Requires(value = { os.linux || os.macOs }, reason = "Uses shell commands")
    def 'build scan is detected in freestyle build via global detection'() {
        given:
        withInjectionConfig {
            globalBuildScanDetection = true
        }
        FreeStyleProject p = j.createFreeStyleProject()
        p.setScm(new ExtractResourceSCM(this.class.getResource('/gradle/wrapper.zip')))
        p.buildersList.add(new CreateFileBuilder('settings.gradle', ''))
        p.buildersList.add(new CreateFileBuilder('build.gradle', """
            buildScan {
                termsOfServiceUrl = 'https://gradle.com/terms-of-service'
                termsOfServiceAgree = 'yes'
            }
            task hello { doLast { println 'Hello' } }
        """))
        p.buildersList.add(new Shell('./gradlew --scan --no-daemon hello'))

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        println JenkinsRule.getLog(build)
        def action = build.getAction(BuildScanAction)
        action != null
        action.scanUrls.size() == 1
        new URL(action.scanUrls.get(0))
    }

    @Requires(value = { os.linux || os.macOs }, reason = "Uses shell commands")
    def 'build scan is detected in pipeline build via global detection'() {
        given:
        withInjectionConfig {
            globalBuildScanDetection = true
        }
        gradleInstallationRule.addInstallation()
        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition("""
node {
    stage('Build') {
        def gradleHome = tool name: '${gradleInstallationRule.gradleVersion}', type: 'gradle'
        writeFile file: 'settings.gradle', text: ''
        writeFile file: 'build.gradle', text: "buildScan { termsOfServiceUrl = 'https://gradle.com/terms-of-service'; termsOfServiceAgree = 'yes' }"
        sh "'\${gradleHome}/bin/gradle' help --scan --no-daemon"
    }
}
""", false))

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        println JenkinsRule.getLog(build)
        def action = build.getAction(BuildScanAction)
        action != null
        action.scanUrls.size() == 1
        new URL(action.scanUrls.get(0))
    }
}
