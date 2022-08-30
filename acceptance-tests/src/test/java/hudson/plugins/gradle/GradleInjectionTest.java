package hudson.plugins.gradle;

import org.apache.commons.io.FileUtils;
import org.jenkinsci.test.acceptance.controller.JenkinsController;
import org.jenkinsci.test.acceptance.controller.LocalController;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.gradle.GradleInstallation;
import org.jenkinsci.test.acceptance.plugins.gradle.GradleStep;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.jenkinsci.test.acceptance.Matchers.containsString;

@WithPlugins("gradle")
public class GradleInjectionTest extends AbstractAcceptanceTest {

    private static final String GRADLE_VERSION = "Gradle 7.5.1";

    @Inject
    private JenkinsController jenkinsController;

    @Before
    public void beforeEach() throws IOException {
        if (jenkinsController instanceof LocalController) {
            File jenkinsHome = ((LocalController) jenkinsController).getJenkinsHome();
            File updatesDirectory = new File(jenkinsHome, "updates");
            FileUtils.copyFileToDirectory(
                resource("/hudson.plugins.gradle.GradleInstaller").asFile(), updatesDirectory);
        }
    }

    @Test
    public void freestyleJobSendsBuildScan() {
        // given
        GradleInstallation.installGradle(jenkins, GRADLE_VERSION, GRADLE_VERSION);
        addGlobalEnvironmentVariables(
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_INJECTION", "true",
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION", "3.11.1",
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL", "https://scans.gradle.com"
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

        // TODO: Check that build scan is visible on the page
    }
}
