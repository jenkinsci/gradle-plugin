package hudson.plugins.gradle

import org.junit.Rule
import org.junit.rules.RuleChain

class GradleAbstractIntegrationTest extends AbstractIntegrationTest {

    @Rule
    public final RuleChain rules = RuleChain.outerRule(noSpaceInTmpDirs).around(j).around(gradleInstallationRule)
}
