package hudson.plugins.gradle;

import hudson.util.VersionNumber;
import org.jenkinsci.test.acceptance.plugins.gradle.GradleInstallation;
import org.jenkinsci.test.acceptance.plugins.gradle.GradleStep;
import org.jenkinsci.test.acceptance.po.Action;
import org.jenkinsci.test.acceptance.po.ActionPageObject;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.ContainerPageObject;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.Jenkins;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.jenkinsci.test.acceptance.Matchers.containsString;
import static org.junit.Assume.assumeTrue;

public class GradleEnterpriseErrorsTest extends AbstractAcceptanceTest {

    private static final String AGENT_VERSION = "3.13.3";
    private static final String GRADLE_VERSION = "Gradle 7.5.1";

    @Before
    public void beforeEach() {
        GradleInstallation.installGradle(jenkins, GRADLE_VERSION, GRADLE_VERSION);

        enableBuildScansForGradle(mockGeServer.getAddress(), AGENT_VERSION);
    }

    @Test
    public void checkForGradleEnterpriseErrors() {
        assumeTrue(
            "The UX is different for now in Jenkins 2.519: the action shows up in a new hamburger menu",
            jenkins.getVersion().isOlderThan(new VersionNumber("2.519"))
        );
        // given
        setCheckForGradleEnterpriseErrors();
        FreeStyleJob job = jenkins.jobs.create(FreeStyleJob.class);

        job.copyDir(resource("/simple_gradle_project"));
        GradleStep gradle = job.addBuildStep(GradleStep.class);
        gradle.setVersion(GRADLE_VERSION);
        gradle.setSwitches("--no-daemon");
        gradle.setTasks("helloWorld -Dcom.gradle.scan.trigger-synthetic-error=true -Ddevelocity.scan.trigger-synthetic-error=true");
        job.save();

        // when
        Build build = job.startBuild();

        // then
        build.shouldFail();
        assertBuildScanNotPublished(build);

        // single build has the error
        build.open();
        assertThat(build.find(By.cssSelector(".alert")).getText(),
            containsString("Develocity Gradle plugin errors detected. Please check the logs for details."));

        jenkins.action(GeErrorPage.class)
            .canClickActionButton()
            .hasRowForBuild(build);
    }

    @ActionPageObject("develocity")
    public static class GeErrorPage extends Action {

        public GeErrorPage(ContainerPageObject parent, String relative) {
            super(parent, relative);
        }

        @Override
        public boolean isApplicable(ContainerPageObject po) {
            return po instanceof Jenkins;
        }

        public GeErrorPage canClickActionButton() {
            // Go to home page
            parent.open();
            String label = "Develocity";
            find(By.linkText(label)).click();
            waitFor(by.xpath("//h1[normalize-space(text()) = '%s']", label));
            return this;
        }

        public GeErrorPage hasRowForBuild(Build build) {
            open();
            WebElement firstRow = find(by.xpath("//table[@id = 'gradle-enterprise-errors']/tbody/tr[1]"));
            firstRow.findElement(by.link(build.url.toString().replace(parent.url("/").toString(), "")));
            firstRow.findElement(by.xpath("//td//*[local-name()='svg' and @tooltip='FAILURE']", build.getResult()));
            firstRow.findElement(by.xpath("//td/img[@tooltip = '%s']", "Gradle"));
            firstRow.findElement(by.xpath("//td[normalize-space(text()) = '%s']", build.job.name));
            return this;
        }
    }

    private void assertBuildScanNotPublished(Build build) {
        String output = build.getConsole();
        assertThat(output, containsString("Internal error in Gradle Enterprise Gradle plugin"));
        assertThat(output, not(containsString("Publishing build scan...")));
        assertThat(output, not(containsString("Publishing Build Scan...")));
    }

}
