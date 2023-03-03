package hudson.plugins.gradle

import org.junit.Rule
import org.junit.rules.RuleChain

/**
 * Base class for tests that need a Jenkins instance and Gradle tool.
 */
abstract class BaseGradleIntegrationTest extends AbstractIntegrationTest {

    public final GradleInstallationRule gradleInstallationRule = new GradleInstallationRule(j)

    @Rule
    public final RuleChain rules = RuleChain.outerRule(noSpaceInTmpDirs).around(j).around(gradleInstallationRule)

    Map getDefaults() {
        [
            gradleName        : gradleInstallationRule.gradleVersion,
            useWorkspaceAsHome: true,
            switches          : '--no-daemon'
        ]
    }
}
