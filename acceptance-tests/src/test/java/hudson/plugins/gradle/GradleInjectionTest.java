package hudson.plugins.gradle;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.text.StrSubstitutor;
import org.apache.commons.lang3.SystemUtils;
import org.hamcrest.Matcher;
import org.jenkinsci.test.acceptance.junit.Resource;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.gradle.GradleInstallation;
import org.jenkinsci.test.acceptance.plugins.gradle.GradleStep;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.Slave;
import org.jenkinsci.test.acceptance.po.WorkflowJob;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.jenkinsci.test.acceptance.Matchers.containsString;
import static org.junit.Assume.assumeTrue;

@WithPlugins("gradle")
public class GradleInjectionTest extends AbstractAcceptanceTest {

    private static final String AGENT_VERSION = "3.11.1";
    private static final String GRADLE_VERSION = "Gradle 7.5.1";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void beforeEach() {
        GradleInstallation.installGradle(jenkins, GRADLE_VERSION, GRADLE_VERSION);

        addGlobalEnvironmentVariables(
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_INJECTION", "true",
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION", AGENT_VERSION,
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL", mockGeServer.getAddress().toString()
        );
    }

    @Test
    public void freestyleJobPublishesBuildScan() {
        // given
        FreeStyleJob job = jenkins.jobs.create(FreeStyleJob.class);

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
        assertBuildScanPublished(build);

        MockGeServer.ScanTokenRequest scanTokenRequest = mockGeServer.getLastScanTokenRequest();
        assertThat(scanTokenRequest, notNullValue());
        assertThat(scanTokenRequest.agentVersion, is(equalTo(AGENT_VERSION)));

        // TODO: Check that build scan is visible on the page
    }

    @Test
    @WithPlugins("pipeline-model-definition")
    public void pipelineJobPublishesBuildScan() {
        assumeTrue(SystemUtils.IS_OS_UNIX);

        // given
        WorkflowJob job = jenkins.jobs.create(WorkflowJob.class);

        String pipelineTemplate = resource("/simple_gradle_project.groovy.template").asText();
        String pipeline =
            new StrSubstitutor(
                ImmutableMap.of(
                    "copy_resource_step", copyResourceDirStep(resource("/simple_gradle_project")),
                    "gradle_version", GRADLE_VERSION,
                    "gradle_arguments", "--no-daemon helloWorld"
                ))
                .replace(pipelineTemplate);

        job.script.set(pipeline);
        job.sandbox.check();
        job.save();

        // when
        Build build = job.startBuild();

        // then
        build.shouldSucceed();
        assertBuildScanPublished(build);

        MockGeServer.ScanTokenRequest scanTokenRequest = mockGeServer.getLastScanTokenRequest();
        assertThat(scanTokenRequest, notNullValue());
        assertThat(scanTokenRequest.agentVersion, is(equalTo(AGENT_VERSION)));
    }

    @Test
    public void skipInjectionIfPluginAlreadyApplied() {
        // given
        String expectedAgentVersion = "3.10.3";

        FreeStyleJob job = jenkins.jobs.create(FreeStyleJob.class);

        job.copyResource(resource("/simple_gradle_project/build.gradle"), "build.gradle");
        job.copyResource(settingsWithGradleEnterprise(expectedAgentVersion), "settings.gradle");
        GradleStep gradle = job.addBuildStep(GradleStep.class);
        gradle.setVersion(GRADLE_VERSION);
        gradle.setSwitches("--no-daemon");
        gradle.setTasks("helloWorld");
        job.save();

        // when
        Build build = job.startBuild();

        // then
        build.shouldSucceed();
        assertBuildScanPublished(build, false);

        MockGeServer.ScanTokenRequest scanTokenRequest = mockGeServer.getLastScanTokenRequest();
        assertThat(scanTokenRequest, notNullValue());
        assertThat(scanTokenRequest.agentVersion, is(equalTo(expectedAgentVersion)));
    }

    @Test
    public void injectionWorksOnAgents() throws Exception {
        // given
        Slave agent = agentController.install(jenkins).get();

        FreeStyleJob job = jenkins.jobs.create(FreeStyleJob.class);
        job.configure();
        job.setLabelExpression(agent.getName());

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
        assertThat(build.getNode().getName(), equalTo(agent.getName()));
        assertBuildScanPublished(build);
    }

    private void assertBuildScanPublished(Build build) {
        assertBuildScanPublished(build, true);
    }

    private void assertBuildScanPublished(Build build, boolean requireAppliedViaInitScript) {
        String output = build.getConsole();

        Matcher<String> appliedViaInitScriptMatcher =
            containsString("Applying com.gradle.enterprise.gradleplugin.GradleEnterprisePlugin via init script");
        assertThat(output, requireAppliedViaInitScript ? appliedViaInitScriptMatcher : not(appliedViaInitScriptMatcher));

        assertThat(output, containsString("> Task :helloWorld"));
        assertThat(output, containsString("Hello, World!"));
        assertThat(output, containsString("Publishing build scan..." + System.lineSeparator() + mockGeServer.publicBuildScanId()));
    }

    private Resource settingsWithGradleEnterprise(String gePluginVersion) {
        String settingsTemplate = resource("/settings_with_ge.gradle.template").asText();
        String settings =
            new StrSubstitutor(
                ImmutableMap.of(
                    "ge_plugin_version", gePluginVersion,
                    "server", mockGeServer.getAddress()
                ))
                .replace(settingsTemplate);

        try {
            File tmp = tempFolder.newFile();
            FileUtils.writeStringToFile(tmp, settings, StandardCharsets.UTF_8);

            return new Resource(tmp.toURI().toURL());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
