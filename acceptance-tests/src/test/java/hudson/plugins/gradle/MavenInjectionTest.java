package hudson.plugins.gradle;

import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.maven.MavenBuildStep;
import org.jenkinsci.test.acceptance.plugins.maven.MavenInstallation;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.junit.Test;
import org.openqa.selenium.By;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.jenkinsci.test.acceptance.Matchers.containsString;

@WithPlugins("gradle")
public class MavenInjectionTest extends AbstractAcceptanceTest {

    private static final String MAVEN_VERSION = "3.8.6";

    @Test
    public void freestyleJobSendsBuildScan() {
        // given
        MavenInstallation.installMaven(jenkins, MAVEN_VERSION, MAVEN_VERSION);

        addGlobalEnvironmentVariables(
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_INJECTION", "true",
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_EXTENSION_VERSION", "1.15.1",
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL", "https://scans.gradle.com"
        );

        FreeStyleJob job = jenkins.jobs.create();

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

        String output = build.getConsole();
        assertThat(output, containsString("[INFO] 3 goals, 3 executed"));
    }
}
