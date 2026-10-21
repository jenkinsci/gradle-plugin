package hudson.plugins.gradle.injection

import hudson.plugins.gradle.BaseGradleIntegrationTest
import hudson.plugins.gradle.BuildScanAction
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.steps.durable_task.DurableTaskStep
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Requires

class BuildScanDetectionWithDurableTaskStepUseWatchingIntegrationTest extends BaseGradleIntegrationTest {

    private boolean useWatching

    def setup() {
        // Read once in a static initializer, so a system property set by a rule may come too late
        useWatching = DurableTaskStep.USE_WATCHING
        DurableTaskStep.USE_WATCHING = true
    }

    def cleanup() {
        DurableTaskStep.USE_WATCHING = useWatching
    }

    @Requires(value = { os.linux || os.macOs }, reason = "Uses shell commands")
    def 'build scans from steps running on an agent are detected when DurableTaskStep.USE_WATCHING=true'() {
        given:
        withEnrichedSummaryConfig {
            globalBuildScanDetection = true
        }
        createSlave('agent')
        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition("""
node('agent') {
    sh '''echo "Publishing build scan..."
echo "https://scans.gradle.com/s/agent1"'''
    sh '''echo "Publishing build scan..."
echo "https://scans.gradle.com/s/agent2"'''
}
""", false))

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        println JenkinsRule.getLog(build)
        def action = build.getAction(BuildScanAction)
        action != null
        action.scanUrls == ['https://scans.gradle.com/s/agent1', 'https://scans.gradle.com/s/agent2']
    }
}
