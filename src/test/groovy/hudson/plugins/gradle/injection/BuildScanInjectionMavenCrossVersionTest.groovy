package hudson.plugins.gradle.injection

import hudson.plugins.gradle.BuildScanAction
import hudson.plugins.gradle.BuildScanBuildWrapper
import hudson.tasks.Maven
import org.junit.Rule
import org.junit.rules.RuleChain
import org.jvnet.hudson.test.CreateFileBuilder
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Unroll

@Unroll
class BuildScanInjectionMavenCrossVersionTest extends BaseInjectionIntegrationTest {
    @Rule
    public final RuleChain rules = RuleChain.outerRule(noSpaceInTmpDirs).around(j).around(mavenInstallationRule)

    def 'build scan is discovered from Maven build - #mavenVersion'(String mavenVersion) {
        given:
        mavenInstallationRule.mavenVersion = mavenVersion
        mavenInstallationRule.addInstallation()

        withGlobalEnvVars {
            put('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_INJECTION', 'true')
            put('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_EXTENSION_VERSION', '1.14.2')
            put('JENKINSGRADLEPLUGIN_CCUD_EXTENSION_VERSION', '1.10.1')
        }

        def p = j.createFreeStyleProject()
        p.buildWrappersList.add(new BuildScanBuildWrapper())
        p.buildersList.add(new CreateFileBuilder('pom.xml', MavenSnippets.simplePom()))
        p.buildersList.add(new CreateFileBuilder('.mvn/gradle-enterprise.xml', MavenSnippets.gradleEnterpriseConfiguration()))
        p.buildersList.add(new Maven('package', mavenVersion))

        def slave = createSlave('foo')
        p.setAssignedNode(slave)

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        println JenkinsRule.getLog(build)
        if (mavenVersion >= '3.3.1') {
            def action = build.getAction(BuildScanAction)
            action.scanUrls.size() == 1
            new URL(action.scanUrls.get(0))
        } else {
            def action = build.getAction(BuildScanAction)
            action.scanUrls.size() == 0
        }

        where:
        mavenVersion << [
            // Uncomment after 1.15.3 is released
            //    '3.1.1',
            //    '3.2.1',
            //    '3.2.5',
            '3.3.1',
            '3.5.4',
            '3.8.6'
        ]
    }
}
