package hudson.plugins.gradle

import hudson.model.FreeStyleProject
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.recipes.LocalData

class CompatibilityTest {
    @Rule
    public JenkinsRule j = new JenkinsRule()

    @Test
    @LocalData
    void read_old_configuration_file() {
        FreeStyleProject p = (FreeStyleProject) j.getInstance().getItem("old");
        Gradle gradle = p.getBuildersList().get(hudson.plugins.gradle.Gradle.class)
        Gradle reference = configuredGradle()

        assert gradle.description == reference.description
        assert gradle.switches == reference.switches
        assert gradle.tasks == reference.tasks
        assert gradle.rootBuildScriptDir == reference.rootBuildScriptDir
        assert gradle.buildFile == reference.buildFile
        assert gradle.gradleName == reference.gradleName
        assert gradle.useWrapper == reference.useWrapper
        assert gradle.makeExecutable == reference.makeExecutable
        assert gradle.fromRootBuildScriptDir == reference.fromRootBuildScriptDir
        assert gradle.useWorkspaceAsHome == reference.useWorkspaceAsHome
        assert gradle.passAsProperties == reference.passAsProperties
    }

    private Gradle configuredGradle() {
        new Gradle("description", "switches", 'tasks', "rootBuildScript",
                "buildFile.gradle", '2.14', true, true, true,
                true, true)
    }
}
