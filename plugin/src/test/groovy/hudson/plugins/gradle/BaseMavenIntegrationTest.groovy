package hudson.plugins.gradle

import org.junit.Rule
import org.junit.rules.RuleChain

/**
 * Base class for tests that need a Jenkins instance and Maven tool.
 */
abstract class BaseMavenIntegrationTest extends AbstractIntegrationTest {

    public final MavenInstallationRule mavenInstallationRule = new MavenInstallationRule(j)

    @Rule
    public final RuleChain rules = RuleChain.outerRule(noSpaceInTmpDirs).around(j).around(mavenInstallationRule)
}
