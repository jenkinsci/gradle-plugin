package hudson.plugins.gradle.injection

import hudson.plugins.gradle.BaseMavenIntegrationTest
import hudson.plugins.gradle.BuildScanAction
import hudson.plugins.gradle.BuildScanBuildWrapper
import hudson.tasks.Maven
import org.jvnet.hudson.test.CreateFileBuilder
import spock.lang.Unroll

@Unroll
class BuildScanInjectionMavenCrossVersionTest extends BaseMavenIntegrationTest {

    private static final String MINIMUM_SUPPORTED_MAVEN_VERSION = '3.3.1'

    def 'build scan is discovered from Maven build - #mavenVersion'(String mavenVersion) {
        given:
        mavenInstallationRule.mavenVersion = mavenVersion
        mavenInstallationRule.addInstallation()

        withInjectionConfig {
            enabled = true
            server = 'https://scans.gradle.com'
            injectMavenExtension = true
            injectCcudExtension = true
        }

        def p = j.createFreeStyleProject()
        p.buildWrappersList.add(new BuildScanBuildWrapper())
        p.buildersList.add(new CreateFileBuilder('pom.xml', MavenSnippets.simplePom(MavenSnippets.httpsPluginRepositories())))
        p.buildersList.add(new CreateFileBuilder('.mvn/gradle-enterprise.xml', MavenSnippets.gradleEnterpriseConfiguration()))
        p.buildersList.add(new Maven('package', mavenVersion))

        def slave = createSlave('foo')
        p.setAssignedNode(slave)

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        if (mavenVersion >= MINIMUM_SUPPORTED_MAVEN_VERSION) {
            def action = build.getAction(BuildScanAction)
            action.scanUrls.size() == 1
            new URL(action.scanUrls.get(0))
        } else {
            def action = build.getAction(BuildScanAction)
            action == null
        }

        where:
        mavenVersion << [
            '3.0.4',
            '3.0.5',
            '3.1.1',
            '3.2.1',
            '3.2.5',
            '3.3.1',
            '3.5.4',
            '3.8.7',
            '3.9.0'
        ]
    }
}
