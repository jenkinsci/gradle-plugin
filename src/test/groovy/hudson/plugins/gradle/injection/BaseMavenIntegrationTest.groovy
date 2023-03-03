package hudson.plugins.gradle.injection

import hudson.plugins.gradle.AbstractIntegrationTest
import org.junit.Rule
import org.junit.rules.RuleChain

/**
 * Base class for tests that need a Jenkins instance and Maven tool.
 */
abstract class BaseMavenIntegrationTest extends AbstractIntegrationTest {

    @Rule
    public final RuleChain rules = RuleChain.outerRule(noSpaceInTmpDirs).around(j).around(mavenInstallationRule)
}
