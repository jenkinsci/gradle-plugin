package hudson.plugins.gradle

import org.junit.Rule
import org.junit.rules.RuleChain
import spock.lang.Tag

/**
 * Base class for tests that need a Jenkins instance and Maven tool.
 */
@Tag('parallel')
abstract class BaseMavenIntegrationTest extends AbstractIntegrationTest {

    public final MavenInstallationRule mavenInstallationRule = new MavenInstallationRule(j)

    @Rule
    public final RuleChain rules = RuleChain.outerRule(noSpaceInTmpDirs).around(j).around(mavenInstallationRule)
}
