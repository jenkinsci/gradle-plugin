package hudson.plugins.gradle.injection

import hudson.plugins.gradle.BaseGradleIntegrationTest
import hudson.plugins.gradle.BuildScanAction
import hudson.slaves.DumbSlave
import org.jenkinsci.plugins.workflow.steps.durable_task.DurableTaskStep
import spock.lang.Unroll

@Unroll
class BuildScanInjectionGradleWithDurableTaskStepUseWatchingIntegrationTest extends BaseGradleIntegrationTest {

    private static final String MSG_INIT_SCRIPT_APPLIED = "Connection to Develocity: http://foo.com"

    private boolean useWatching

    def setup() {
        // Read once in a static initializer, so a system property set by a rule may come too late
        useWatching = DurableTaskStep.USE_WATCHING
        DurableTaskStep.USE_WATCHING = true
    }

    def cleanup() {
        DurableTaskStep.USE_WATCHING = useWatching
    }

    def "captures build agent errors in pipeline build if DurableTaskStep.USE_WATCHING=true"() {
        given:
        gradleInstallationRule.addInstallation()
        def gradleVersion = gradleInstallationRule.gradleVersion

        DumbSlave agent = createSlave()

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
        with(secondRun.getAction(BuildScanAction)) {
            scanUrls.isEmpty()
            hasGradleErrors
            !hasMavenErrors
        }
    }

    def "credentials are always masked in logs"() {
        given:
        def secret = 'confidential'
        registerCredentials('my-creds', secret)

        def gradleVersion = '8.6'
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

    private DumbSlave createSlave() {
        withInjectionConfig {
            server = 'http://foo.com'
        }

        return createSlave('foo')
    }

}
