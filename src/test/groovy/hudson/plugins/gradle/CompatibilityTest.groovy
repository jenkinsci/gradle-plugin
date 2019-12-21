package hudson.plugins.gradle

import hudson.model.FreeStyleProject
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.recipes.LocalData

class CompatibilityTest {
    @Rule
    public final JenkinsRule j = new JenkinsRule()

    @Test
    @LocalData
    void read_old_configuration_files() {
        FreeStyleProject p = (FreeStyleProject) j.jenkins.getItem('old')
        Gradle gradle = p.getBuildersList().get(Gradle)
        Gradle reference = configuredGradle()

        assert gradle.switches == reference.switches
        assert gradle.tasks == reference.tasks
        assert gradle.rootBuildScriptDir == reference.rootBuildScriptDir
        assert gradle.buildFile == reference.buildFile
        assert gradle.gradleName == reference.gradleName
        assert gradle.useWrapper == reference.useWrapper
        assert gradle.makeExecutable == reference.makeExecutable
        assert gradle.useWorkspaceAsHome == reference.useWorkspaceAsHome
        assert gradle.passAllAsProjectProperties == reference.passAllAsProjectProperties
        assert gradle.systemProperties == reference.systemProperties
        assert gradle.passAllAsSystemProperties == reference.passAllAsSystemProperties
        assert gradle.projectProperties == reference.projectProperties
        assert gradle.wrapperLocation == reference.wrapperLocation

        def installations = j.jenkins.getDescriptorByType(hudson.plugins.gradle.Gradle.DescriptorImpl).getInstallations()
        assert installations.size() == 1
        assert installations[0].name == '2.14'
    }

    @Test
    @LocalData
    void read_configuration_files_older_than_1_27() {
        FreeStyleProject p = (FreeStyleProject) j.jenkins.getItem('old')
        Gradle gradle = p.getBuildersList().get(Gradle)
        Gradle reference = configuredGradle()
        reference.passAllAsSystemProperties = true
        reference.passAllAsProjectProperties = false

        assert gradle.switches == reference.switches
        assert gradle.tasks == reference.tasks
        assert gradle.rootBuildScriptDir == reference.rootBuildScriptDir
        assert gradle.buildFile == reference.buildFile
        assert gradle.gradleName == reference.gradleName
        assert gradle.useWrapper == reference.useWrapper
        assert gradle.makeExecutable == reference.makeExecutable
        assert gradle.useWorkspaceAsHome == reference.useWorkspaceAsHome
        assert gradle.passAllAsProjectProperties == reference.passAllAsProjectProperties
        assert gradle.systemProperties == reference.systemProperties
        assert gradle.passAllAsSystemProperties == reference.passAllAsSystemProperties
        assert gradle.projectProperties == reference.projectProperties
        assert gradle.wrapperLocation == reference.wrapperLocation

        def installations = j.jenkins.getDescriptorByType(hudson.plugins.gradle.Gradle.DescriptorImpl).getInstallations()
        assert installations.size() == 1
        assert installations[0].name == '2.14'
    }

    @Test
    @LocalData
    void convert_old_build_scan_actions() {
        FreeStyleProject p = (FreeStyleProject) j.jenkins.getItem('old')
        def build = p.getBuildByNumber(1)
        def buildScanActions = build.getAllActions().findAll { it instanceof BuildScanAction } as List<BuildScanAction>

        assert buildScanActions.size() == 2
        assert buildScanActions[0].scanUrls == ['https://gradle.com/s/trs4je7zh3ysc']
        assert buildScanActions[1].scanUrls == ['https://gradle.com/s/uaxunlpjhzoda']
    }

    private Gradle configuredGradle() {
        new Gradle(switches: 'switches', tasks: 'tasks', rootBuildScriptDir: 'rootBuildScript',
                buildFile: 'buildFile.gradle', gradleName: '2.14', useWrapper: true, makeExecutable: true, wrapperLocation: 'rootBuildScript',
                useWorkspaceAsHome: true, passAllAsProjectProperties: true)
    }
}
