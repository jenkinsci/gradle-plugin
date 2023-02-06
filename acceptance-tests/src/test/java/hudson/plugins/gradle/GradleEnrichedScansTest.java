package hudson.plugins.gradle;

import com.google.common.collect.ImmutableMap;
import org.jenkinsci.test.acceptance.junit.Resource;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.gradle.GradleInstallation;
import org.jenkinsci.test.acceptance.plugins.gradle.GradleStep;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.jenkinsci.test.acceptance.Matchers.containsString;

@WithPlugins("gradle")
public class GradleEnrichedScansTest extends AbstractAcceptanceTest {

    private static final String AGENT_VERSION = "3.11.1";
    private static final String GRADLE_VERSION = "Gradle 7.5.1";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void beforeEach() {
        GradleInstallation.installGradle(jenkins, GRADLE_VERSION, GRADLE_VERSION);

        enableEnrichedBuildScans();
    }

    @Test
    public void freestyleJobPublishesEnrichedBuildScan() {
        // given
        FreeStyleJob job = jenkins.jobs.create(FreeStyleJob.class);

        job.copyResource(resource("/simple_gradle_project/build.gradle"), "build.gradle");
        job.copyResource(settingsWithGradleEnterprise(), "settings.gradle");
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
        assertThat(output, containsString("> Task :helloWorld"));
        assertThat(output, containsString("Hello, World!"));
        assertThat(output, containsString("Publishing build scan..." + System.lineSeparator() + mockGeServer.publicBuildScanId()));

        build.open();
        WebElement scanDetails = build.find(By.id("scanDetails"));
        assertThat(scanDetails.findElement(By.className("project-name")).getText(), equalTo("foo"));
        assertThat(scanDetails.findElement(By.className("requested-tasks")).getText(), equalTo("[clean, build]"));
        assertThat(scanDetails.findElement(By.className("build-tool-version")).getText(), equalTo("7.0"));
        assertThat(scanDetails.findElement(By.className("build-scan-link")).getAttribute("href"), equalTo(mockGeServer.publicBuildScanId()));

        MockGeServer.ScanTokenRequest scanTokenRequest = mockGeServer.getLastScanTokenRequest();
        assertThat(scanTokenRequest, notNullValue());
        assertThat(scanTokenRequest.agentVersion, is(equalTo(AGENT_VERSION)));
    }

    private Resource settingsWithGradleEnterprise() {
        String template = resource("/settings_with_ge.gradle.template").asText();
        String resolvedTemplate = resolveTemplate(template, ImmutableMap.of(
                "ge_plugin_version", AGENT_VERSION,
                "server", mockGeServer.getAddress().toString()
        ));

        return createTmpFile(tempFolder, resolvedTemplate);
    }

}
