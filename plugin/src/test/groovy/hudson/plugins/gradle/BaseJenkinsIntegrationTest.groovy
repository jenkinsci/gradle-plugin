package hudson.plugins.gradle

import org.junit.Rule
import org.junit.rules.RuleChain
import spock.lang.Tag

/**
 * Base class for tests that need a Jenkins instance.
 */
@Tag('parallel')
abstract class BaseJenkinsIntegrationTest extends AbstractIntegrationTest {

    @Rule
    public final RuleChain rules = RuleChain.outerRule(noSpaceInTmpDirs).around(j)
}
