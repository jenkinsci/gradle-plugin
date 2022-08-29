package hudson.plugins.gradle;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

@WithPlugins("gradle")
public class GradlePluginTest extends AbstractJUnitTest {

    @Test
    public void test() {
        FreeStyleJob job = jenkins.jobs.create();

        assertTrue(true);
    }
}
