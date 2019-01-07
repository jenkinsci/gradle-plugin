package hudson.plugins.gradle

import hudson.model.Cause
import hudson.model.FreeStyleBuild
import hudson.model.FreeStyleProject
import hudson.model.ParametersAction
import hudson.model.ParametersDefinitionProperty
import hudson.model.TextParameterDefinition
import hudson.model.TextParameterValue
import hudson.model.queue.QueueTaskFuture
import org.junit.Rule
import org.junit.rules.RuleChain
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Specification

class AbstractIntegrationTest extends Specification {
    final JenkinsRule j = new JenkinsRule()
    final GradleInstallationRule gradleInstallationRule = new GradleInstallationRule(j)
    @Rule
    public final RuleChain rules = RuleChain.outerRule(j).around(gradleInstallationRule)

    Map getDefaults() {
        [gradleName: gradleInstallationRule.gradleVersion, useWorkspaceAsHome: true, switches: '--no-daemon']
    }

    static QueueTaskFuture<FreeStyleBuild> triggerBuildWithParameter(FreeStyleProject p, String parameterName, String value) {
        p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new TextParameterValue(parameterName, value)))
    }

    static addParameter(FreeStyleProject p, String parameterName) {
        p.addProperty(new ParametersDefinitionProperty(new TextParameterDefinition(parameterName, null, null)))
    }

}
