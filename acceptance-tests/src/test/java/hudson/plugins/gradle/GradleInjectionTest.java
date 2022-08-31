package hudson.plugins.gradle;

import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.gradle.GradleInstallation;
import org.jenkinsci.test.acceptance.plugins.gradle.GradleStep;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.jenkinsci.test.acceptance.Matchers.containsString;

@WithPlugins("gradle")
public class GradleInjectionTest extends AbstractAcceptanceTest {

    private static final String GRADLE_VERSION = "Gradle 7.5.1";

    @Test
    public void freestyleJobSendsBuildScan() {
        // given
        GradleInstallation.installGradle(jenkins, GRADLE_VERSION, GRADLE_VERSION);

        addGlobalEnvironmentVariables(
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_INJECTION", "true",
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION", "3.11.1",
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL", mockGeServerAddress().toString()
        );

        FreeStyleJob job = jenkins.jobs.create();

        job.copyDir(resource("/simple_gradle_project"));
        GradleStep gradle = job.addBuildStep(GradleStep.class);
        gradle.setVersion(GRADLE_VERSION);
        gradle.setSwitches("--no-daemon");
        gradle.setTasks("helloWorld");
        job.save();

        // when
        Build build = job.startBuild();

        // then
        build.shouldSucceed();

        String output = build.getConsole();
        assertThat(output, containsString("Applying com.gradle.enterprise.gradleplugin.GradleEnterprisePlugin via init script"));
        assertThat(output, containsString("> Task :helloWorld"));
        assertThat(output, containsString("Hello, World!"));
        assertThat(output, containsString("Publishing build scan..." + System.lineSeparator() + publicBuildScanId()));

        // TODO: Check that build scan is visible on the page
    }
}
