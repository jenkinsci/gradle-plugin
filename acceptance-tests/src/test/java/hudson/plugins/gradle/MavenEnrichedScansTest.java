package hudson.plugins.gradle;

import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.maven.MavenBuildStep;
import org.jenkinsci.test.acceptance.plugins.maven.MavenInstallation;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openqa.selenium.By;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.jenkinsci.test.acceptance.Matchers.containsString;

@WithPlugins("gradle")
public class MavenEnrichedScansTest extends AbstractAcceptanceTest {

    private static final String MAVEN_VERSION = "3.8.6";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void beforeEach() {
        MavenInstallation.installMaven(jenkins, MAVEN_VERSION, MAVEN_VERSION);

        enableEnrichedBuildScansWithServerOrverride(mockGeServer.getAddress());
    }

    @Test
    public void freestyleJobSendsPublicBuildScanButDoNotEnrichIt() {
        // given
        FreeStyleJob job = jenkins.jobs.create(FreeStyleJob.class);

        job.copyDir(resource("/simple_maven_project"));
        job.copyResource(resource("/mvn_extensions_with_ge.xml"), ".mvn/extensions.xml");
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
        assertThat(output, containsString("[INFO] Publishing build scan..." + System.lineSeparator() + "[INFO] https://gradle.com/s/"));

        // Build scans on public instance are not enriched
    }

}
