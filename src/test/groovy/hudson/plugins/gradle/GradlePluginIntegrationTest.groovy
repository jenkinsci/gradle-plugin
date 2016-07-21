package hudson.plugins.gradle

import hudson.model.FreeStyleProject
import org.junit.Rule
import org.junit.rules.RuleChain
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Specification

class GradlePluginIntegrationTest extends Specification {
    private String gradleVersion = '2.14'

    public JenkinsRule j = new JenkinsRule()
    public GradleInstallationRule gradle = new GradleInstallationRule(gradleVersion, j)

    @Rule
    public RuleChain rules = RuleChain.outerRule(j).around(gradle)

    def "can install and run Gradle"() {
        given:
        FreeStyleProject project = j.createFreeStyleProject();

        def gradle = new Gradle("", "", 'help', "", null, gradleVersion, false, false, true,
        true, false)

        project.getBuildersList().add(gradle)

        when:
        def build = j.buildAndAssertSuccess(project);

        then:
        def log = JenkinsRule.getLog(build)
        log.contains("Welcome to Gradle ${gradleVersion}.")
        log.contains("BUILD SUCCESSFUL")
    }

    def "Config roundtrip"() {
        given:
        def before = configuredGradle()

        when:
        def after = j.configRoundtrip(before)

        then:
        before.description == after.description
        before.switches == after.switches
        before.tasks == after.tasks
        before.rootBuildScriptDir == after.rootBuildScriptDir
        before.buildFile == after.buildFile
        before.gradleName == after.gradleName
        before.useWrapper == after.useWrapper
        before.makeExecutable == after.makeExecutable
        before.fromRootBuildScriptDir == after.fromRootBuildScriptDir
        before.useWorkspaceAsHome == after.useWorkspaceAsHome
        before.passAsProperties == after.passAsProperties
    }

    private Gradle configuredGradle() {
        new Gradle("description", "switches", 'tasks', "buildScriptDir",
                "buildFile.gradle", gradleVersion, true, true, true,
                true, true)
    }
}
