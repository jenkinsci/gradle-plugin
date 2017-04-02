package hudson.plugins.gradle

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
}
