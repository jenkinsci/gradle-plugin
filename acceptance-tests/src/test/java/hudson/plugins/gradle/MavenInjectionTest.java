package hudson.plugins.gradle;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang.text.StrSubstitutor;
import org.apache.commons.lang3.SystemUtils;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.maven.MavenBuildStep;
import org.jenkinsci.test.acceptance.plugins.maven.MavenInstallation;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.WorkflowJob;
import org.junit.Test;
import org.openqa.selenium.By;

import java.net.URI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.jenkinsci.test.acceptance.Matchers.containsString;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

@WithPlugins("gradle")
public class MavenInjectionTest extends AbstractAcceptanceTest {

    private static final String GE_TEST_INSTANCE = System.getenv("GRADLE_ENTERPRISE_TEST_INSTANCE");
    private static final URI GE_URL = GE_TEST_INSTANCE != null ? URI.create(GE_TEST_INSTANCE) : null;

    private static final String MAVEN_VERSION = "3.8.6";

    @Test
    public void freestyleJobSendsBuildScan() {
        assumeNotNull(GE_URL);

        // given
        installMavenAndEnableInjection();

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
    @WithPlugins("pipeline-model-definition")
    public void pipelineJobPublishesBuildScan() {
        assumeNotNull(GE_URL);
        assumeTrue(SystemUtils.IS_OS_UNIX);

        // given
        installMavenAndEnableInjection();

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

    private void installMavenAndEnableInjection() {
        MavenInstallation.installMaven(jenkins, MAVEN_VERSION, MAVEN_VERSION);

        addGlobalEnvironmentVariables(
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_INJECTION", "true",
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_EXTENSION_VERSION", "1.15.1",
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL", GE_URL.toString()
        );
    }

    private void assertBuildScanPublished(Build build) {
        String output = build.getConsole();
        boolean hasCacheHit = output.contains("[INFO] Loaded from the build cache");
        assertThat(output, containsString("[INFO] 3 goals, %d executed", hasCacheHit ? 2 : 3));
        assertThat(output, containsString("[INFO] Publishing build scan..." + System.lineSeparator() + "[INFO] %s/s/", GE_URL));
    }
}
