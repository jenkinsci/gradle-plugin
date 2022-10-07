package hudson.plugins.gradle.injection

import org.junit.Rule
import org.junit.rules.RuleChain

class BaseGradleInjectionIntegrationTest extends BaseInjectionIntegrationTest {

    @Rule
    public final RuleChain rules = RuleChain.outerRule(noSpaceInTmpDirs).around(j).around(gradleInstallationRule)

}
