package hudson.plugins.gradle.injection

import hudson.plugins.gradle.BaseMavenIntegrationTest
import hudson.plugins.gradle.BuildScanAction
import hudson.plugins.gradle.BuildScanBuildWrapper
import hudson.tasks.Maven
import org.jvnet.hudson.test.CreateFileBuilder
import spock.lang.Unroll

@Unroll
class BuildScanInjectionMavenCrossVersionIntegrationTest extends BaseMavenIntegrationTest {

    private static final String MINIMUM_SUPPORTED_MAVEN_VERSION = '3.3.1'

    def 'build scan is discovered from Maven build - #mavenVersion'(String mavenVersion) {
        given:
        mavenInstallationRule.mavenVersion = mavenVersion
        mavenInstallationRule.addInstallation()

        withInjectionConfig {
            enabled = true
            server = 'https://scans.gradle.com'
            mavenExtensionVersion = '2.1'
            ccudExtensionVersion = '2.0'
        }

        def p = j.createFreeStyleProject()
        p.buildWrappersList.add(new BuildScanBuildWrapper())
        p.buildersList.add(new CreateFileBuilder('pom.xml', MavenSnippets.simplePom(MavenSnippets.httpsPluginRepositories())))
        p.buildersList.add(new CreateFileBuilder('.mvn/develocity.xml', MavenSnippets.develocityConfiguration()))
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
            '3.0.5',
            '3.1.1',
            '3.2.3',
            '3.2.5',
            '3.3.9',
            '3.5.4',
            '3.6.3',
            '3.8.8',
            '3.9.9'
        ]
    }

    def setup() {
        Thread.start {
            try {
                sleep(60 * 1000)
                if (j.jenkins == null) return

                println "\n========================================"
                println "   BACKGROUND MONITOR: 60s ELAPSED"
                println "   Attempting Thread Dump of 'slave0'"
                println "========================================\n"

                def computer = j.jenkins.getComputer("slave0")

                if (computer) {
                    computer.getThreadDump().each { name, stack ->
                        println "Thread: $name"
                        println "$stack"
                        println "---------------------------------"
                    }
                } else {
                    println "❌ Could not find agent 'slave0'. It might be offline or not created yet."
                }

            } catch (Exception e) {
                println "Background monitor stopped: ${e.message}"
            }
        }
    }

}
