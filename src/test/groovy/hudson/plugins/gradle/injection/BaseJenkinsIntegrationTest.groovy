package hudson.plugins.gradle.injection

import hudson.plugins.gradle.AbstractIntegrationTest
import org.junit.Rule
import org.junit.rules.RuleChain

/**
 * Base class for tests that need a Jenkins instance.
 */
abstract class BaseJenkinsIntegrationTest extends AbstractIntegrationTest {

    @Rule
    public final RuleChain rules = RuleChain.outerRule(noSpaceInTmpDirs).around(j)
}
