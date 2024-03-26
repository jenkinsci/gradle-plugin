package hudson.plugins.gradle;

import com.google.common.collect.ImmutableMap;
import org.hamcrest.Matcher;
import org.jenkinsci.test.acceptance.junit.Resource;
import org.jenkinsci.test.acceptance.junit.WithOS;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.envinject.EnvInjectAction;
import org.jenkinsci.test.acceptance.plugins.git.GitScm;
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
import org.openqa.selenium.By;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.jenkinsci.test.acceptance.Matchers.containsString;

public class GradleInjectionTest extends AbstractAcceptanceTest {

    private static final String AGENT_VERSION = "3.11.1";
    private static final String GRADLE_VERSION = "Gradle 7.5.1";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void beforeEach() {
        GradleInstallation.installGradle(jenkins, GRADLE_VERSION, GRADLE_VERSION);

        enableBuildScansForGradle(mockGeServer.getAddress(), AGENT_VERSION);
    }

    @Test
    @WithPlugins("git")
    public void appliesAutoInjectionIfRepositoryShouldBeIncluded() {
        // given
        setGitRepositoryFilters(
            filter()
                .include("gradle")
                .exclude("maven")
                .build()
        );

        FreeStyleJob job = jenkins.jobs.create(FreeStyleJob.class);
        job.useScm(GitScm.class)
            .url("https://github.com/c00ler/simple-gradle-project.git")
            .branch("main");
        GradleStep gradle = job.addBuildStep(GradleStep.class);
        gradle.setVersion(GRADLE_VERSION);
        gradle.setSwitches("--no-daemon");
        gradle.setTasks("help");
        job.save();

        // when
        Build build = job.startBuild();

        // then
        build.shouldSucceed();
        assertBuildScanPublished(build, "> Task :help", "To see a list of available tasks, run gradle tasks");

        MockGeServer.ScanTokenRequest scanTokenRequest = mockGeServer.getLastScanTokenRequest();
        assertThat(scanTokenRequest, notNullValue());
        assertThat(scanTokenRequest.agentVersion, is(equalTo(AGENT_VERSION)));
    }

    @Test
    @WithPlugins("git")
    public void skipsAutoInjectionIfRepositoryShouldBeExcluded() {
        // given
        setGitRepositoryFilters(
            filter()
                .include("gradle")
                .exclude("simple-gradle")
                .build()
        );

        FreeStyleJob job = jenkins.jobs.create(FreeStyleJob.class);
        job.useScm(GitScm.class)
            .url("https://github.com/c00ler/simple-gradle-project.git")
            .branch("main");
        GradleStep gradle = job.addBuildStep(GradleStep.class);
        gradle.setVersion(GRADLE_VERSION);
        gradle.setSwitches("--no-daemon");
        gradle.setTasks("help");
        job.save();

        // when
        Build build = job.startBuild();

        // then
        build.shouldSucceed();
        assertBuildScanNotPublished(build, "> Task :help", "To see a list of available tasks, run gradle tasks");

        MockGeServer.ScanTokenRequest scanTokenRequest = mockGeServer.getLastScanTokenRequest();
        assertThat(scanTokenRequest, is(nullValue()));
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

        build.open();
        build.check(build.find(By.linkText(mockGeServer.publicBuildScanId())));

        MockGeServer.ScanTokenRequest scanTokenRequest = mockGeServer.getLastScanTokenRequest();
        assertThat(scanTokenRequest, notNullValue());
        assertThat(scanTokenRequest.agentVersion, is(equalTo(AGENT_VERSION)));
    }

    @Test
    @WithPlugins("envinject")
    public void accessKeyIsMasked() {
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
        build.action(EnvInjectAction.class).shouldContain("GRADLE_ENTERPRISE_ACCESS_KEY", "[*******]");
    }

    @Test
    @WithPlugins("envinject")
    public void gradlePluginRepoPasswordIsMasked() {
        // given
        setGradlePluginRepositoryPassword("foo");

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
        build.action(EnvInjectAction.class).shouldContain("GRADLE_PLUGIN_REPOSITORY_PASSWORD", "[*******]");
    }

    @Test
    public void logsErrorIfBuildScanUploadFailed() {
        // given
        mockGeServer.rejectUpload();
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

        // and
        String output = build.getConsole();
        assertThat(output, containsString("> Task :helloWorld"));
        assertThat(output, containsString("Hello, World!"));

        assertThat(output, containsString("Publishing build scan..."));
        assertThat(output, not(containsString(mockGeServer.publicBuildScanId())));
        assertThat(output, containsString("Publishing failed."));

        assertThat(output, containsString("Plugin version: " + AGENT_VERSION));
        assertThat(output, containsString("Request URL: " + mockGeServer.getAddress() + "scans/publish/gradle/" + AGENT_VERSION + "/upload"));
        assertThat(output, containsString("Response status code: 502"));
    }

    @Test
    @WithOS(os = {WithOS.OS.MAC, WithOS.OS.LINUX})
    @WithPlugins("pipeline-model-definition")
    public void pipelineJobPublishesBuildScan() {
        // given
        WorkflowJob job = jenkins.jobs.create(WorkflowJob.class);

        String pipelineTemplate = resource("/simple_gradle_project.groovy.template").asText();
        Map<String, String> pipelineTokens =
            ImmutableMap.of(
                "copy_resource_step", copyResourceDirStep(resource("/simple_gradle_project")),
                "gradle_version", GRADLE_VERSION,
                "gradle_arguments", "--no-daemon helloWorld"
            );
        String pipeline = resolveTemplate(pipelineTemplate, pipelineTokens);

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

    @Test
    public void setEnforceUrlPublishesBuildScan() throws Exception {
        // given
        setEnforceUrl();
        setAllowUntrustedServer();
        String expectedAgentVersion = "3.13";

        FreeStyleJob job = jenkins.jobs.create(FreeStyleJob.class);

        job.copyResource(resource("/simple_gradle_project/build.gradle"), "build.gradle");
        // gradle settings pointing to scans.gradle.com
        job.copyResource(settingsWithGradleEnterprise(expectedAgentVersion, PUBLIC_GE_SERVER.toString()), "settings.gradle");
        GradleStep gradle = job.addBuildStep(GradleStep.class);
        gradle.setVersion(GRADLE_VERSION);
        gradle.setSwitches("--no-daemon");
        gradle.setTasks("helloWorld");
        job.save();

        // when
        Build build = job.startBuild();

        // then
        build.shouldSucceed();
        assertBuildScanPublished(build, false, String.format("Enforcing Develocity: %s, allowUntrustedServer: true", mockGeServer.getAddress().toString()));

        MockGeServer.ScanTokenRequest scanTokenRequest = mockGeServer.getLastScanTokenRequest();
        assertThat(scanTokenRequest, notNullValue());
        assertThat(scanTokenRequest.agentVersion, is(equalTo(expectedAgentVersion)));
    }

    private void assertBuildScanNotPublished(Build build, String... requiredLogsLines) {
        String output = build.getConsole();

        assertThat(output, not(containsString("Applying com.gradle.enterprise.gradleplugin.GradleEnterprisePlugin via init script")));
        assertRequiredLogLines(output, requiredLogsLines);
        assertThat(output, not(containsString("Publishing build scan...")));
    }

    private void assertBuildScanPublished(Build build) {
        assertBuildScanPublished(build, true);
    }

    private void assertBuildScanPublished(Build build, boolean requireAppliedViaInitScript) {
        assertBuildScanPublished(build, requireAppliedViaInitScript, "> Task :helloWorld", "Hello, World!");
    }

    private void assertBuildScanPublished(Build build, String... requiredLogsLines) {
        assertBuildScanPublished(build, true, requiredLogsLines);
    }

    private void assertBuildScanPublished(Build build, boolean requireAppliedViaInitScript, String... requiredLogsLines) {
        String output = build.getConsole();

        Matcher<String> appliedViaInitScriptMatcher =
            containsString("Applying com.gradle.enterprise.gradleplugin.GradleEnterprisePlugin via init script");
        assertThat(output, requireAppliedViaInitScript ? appliedViaInitScriptMatcher : not(appliedViaInitScriptMatcher));
        assertRequiredLogLines(output, requiredLogsLines);
        assertThat(output, containsString("Publishing build scan..." + System.lineSeparator() + mockGeServer.publicBuildScanId()));
    }

    private void assertRequiredLogLines(String output, String... requiredLogsLines) {
        for (String line : requiredLogsLines) {
            assertThat(output, containsString(line));
        }
    }

    private Resource settingsWithGradleEnterprise(String gePluginVersion) {
        return settingsWithGradleEnterprise(gePluginVersion, mockGeServer.getAddress().toString());
    }

    private Resource settingsWithGradleEnterprise(String gePluginVersion, String server) {
        String template = resource("/settings_with_ge.gradle.template").asText();
        String resolvedTemplate = resolveTemplate(template, ImmutableMap.of(
            "ge_plugin_version", gePluginVersion,
            "server", server
        ));

        return createTmpFile(tempFolder, resolvedTemplate);
    }
}
