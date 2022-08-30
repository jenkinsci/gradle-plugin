package hudson.plugins.gradle;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.gradle.GradleInstallation;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

@WithPlugins("gradle")
public class GradleInjectionTest extends AbstractJUnitTest {

    @Test
    public void freestyleJobSendsBuildScan() {
        // TODO: Override Gradle installer
        GradleInstallation.installGradle(jenkins, "Gradle 4.10.2", "Gradle 4.10.2");

        assertTrue(true);
    }
}
