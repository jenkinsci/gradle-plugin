package hudson.plugins.gradle;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang.text.StrSubstitutor;
import org.jenkinsci.test.acceptance.junit.WithOS;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.maven.MavenBuildStep;
import org.jenkinsci.test.acceptance.plugins.maven.MavenInstallation;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.Slave;
import org.jenkinsci.test.acceptance.po.WorkflowJob;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.jenkinsci.test.acceptance.Matchers.containsString;

@WithPlugins("gradle")
public class MavenInjectionTest extends AbstractAcceptanceTest {

    private static final String MAVEN_VERSION = "3.8.6";

    @Before
    public void beforeEach() {
        MavenInstallation.installMaven(jenkins, MAVEN_VERSION, MAVEN_VERSION);

        enableBuildScansForMaven();
    }

    @Test
    public void freestyleJobSendsBuildScan() {
        // given
        FreeStyleJob job = jenkins.jobs.create(FreeStyleJob.class);

        job.copyDir(resource("/simple_maven_project"));
        MavenBuildStep maven = job.addBuildStep(MavenBuildStep.class);
        maven.version.select(MAVEN_VERSION);
        maven.targets.set("clean compile");

        maven.check(maven.find(By.name("hudson-plugins-gradle-BuildScanBuildWrapper")));

        job.save();

        // when
        Build build = job.startBuild();

        // then
        build.shouldSucceed();
        assertBuildScanPublished(build);
    }

    @Test
    @WithOS(os = {WithOS.OS.MAC, WithOS.OS.LINUX})
    @WithPlugins("pipeline-model-definition")
    public void pipelineJobPublishesBuildScan() {
        // given
        WorkflowJob job = jenkins.jobs.create(WorkflowJob.class);

        String pipelineTemplate = resource("/simple_maven_project.groovy.template").asText();
        String pipeline =
            new StrSubstitutor(
                ImmutableMap.of(
                    "copy_resource_step", copyResourceDirStep(resource("/simple_maven_project")),
                    "maven_version", MAVEN_VERSION,
                    "maven_arguments", "clean compile"
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
    }

    @Test
    public void injectionWorksOnAgents() throws Exception {
        // given
        Slave agent = agentController.install(jenkins).get();

        FreeStyleJob job = jenkins.jobs.create(FreeStyleJob.class);
        job.configure();
        job.setLabelExpression(agent.getName());

        job.copyDir(resource("/simple_maven_project"));
        MavenBuildStep maven = job.addBuildStep(MavenBuildStep.class);
        maven.version.select(MAVEN_VERSION);
        maven.targets.set("clean compile");

        maven.check(maven.find(By.name("hudson-plugins-gradle-BuildScanBuildWrapper")));

        job.save();

        // when
        Build build = job.startBuild();

        // then
        build.shouldSucceed();
        assertThat(build.getNode().getName(), equalTo(agent.getName()));
        assertBuildScanPublished(build);
    }

    private void assertBuildScanPublished(Build build) {
        String output = build.getConsole();
        assertThat(output, containsString("[INFO] 3 goals, 3 executed"));
        assertThat(output, containsString("[INFO] Publishing build scan..." + System.lineSeparator() + "[INFO] https://gradle.com/s/"));
    }
}
