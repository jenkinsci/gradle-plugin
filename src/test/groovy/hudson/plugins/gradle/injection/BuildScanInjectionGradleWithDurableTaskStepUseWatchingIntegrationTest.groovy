package hudson.plugins.gradle.injection

import hudson.plugins.gradle.BaseGradleIntegrationTest
import hudson.plugins.gradle.BuildScanAction
import hudson.slaves.DumbSlave
import org.junit.Rule
import org.junit.rules.TestRule
import org.jvnet.hudson.test.FlagRule
import spock.lang.PendingFeature
import spock.lang.Unroll

@Unroll
class BuildScanInjectionGradleWithDurableTaskStepUseWatchingIntegrationTest extends BaseGradleIntegrationTest {

    private static final String MSG_INIT_SCRIPT_APPLIED = "Connection to Develocity: http://foo.com"

    @Rule
    public final TestRule durableTaskStepRule = FlagRule.systemProperty("org.jenkinsci.plugins.workflow.steps.durable_task.DurableTaskStep.USE_WATCHING", "true")

    @PendingFeature
    def "cannot capture build agent errors in pipeline build if DurableTaskStep.USE_WATCHING=true"() {
        given:
        def gradleVersion = '8.1.1'
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave('foo')
        def pipelineJob = GradleSnippets.pipelineJobWithError(j, gradleInstallationRule)

        when:
        // first build to download Gradle
        def firstRun = j.buildAndAssertSuccess(pipelineJob)

        then:
        j.assertLogNotContains(MSG_INIT_SCRIPT_APPLIED, firstRun)

        when:
        enableBuildInjection(agent, gradleVersion)
        withInjectionConfig {
            checkForBuildAgentErrors = true
        }
        def secondRun = buildAndAssertFailure(pipelineJob)

        then:
        secondRun.getAction(BuildScanAction) != null
    }

    def "credentials are always masked in logs"() {
        given:
        def secret = 'confidential'
        registerCredentials('my-creds', secret)

        def gradleVersion = '8.1.1'
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()

        DumbSlave agent = createSlave('foo')
        def pipelineJob = GradleSnippets.pipelineJobWithCredentials(j)

        when:
        // first build to download Gradle
        def firstRun = j.buildAndAssertSuccess(pipelineJob)

        then:
        j.assertLogContains('password=****', firstRun)
        j.assertLogNotContains(secret, firstRun)

        when:
        enableBuildInjection(agent, gradleVersion)
        withInjectionConfig {
            checkForBuildAgentErrors = true
        }
        def secondRun = j.buildAndAssertSuccess(pipelineJob)

        then:
        j.assertLogContains('password=****', secondRun)
        j.assertLogNotContains(secret, secondRun)

        cleanup:
        System.err.println('---%<--- agent logs')
        agent.computer.logText.writeLogTo(0, System.err)
        System.err.println('--->%---')
    }

}
