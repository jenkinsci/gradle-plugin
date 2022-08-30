package hudson.plugins.gradle;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import hudson.plugin.gradle.EnvironmentVariablesSettings;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.jenkinsci.test.acceptance.controller.JenkinsController;
import org.jenkinsci.test.acceptance.controller.LocalController;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.junit.Before;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public abstract class AbstractAcceptanceTest extends AbstractJUnitTest {

    @Inject
    public JenkinsController jenkinsController;

    @Before
    public void beforeEach() throws IOException {
        if (jenkinsController instanceof LocalController) {
            File jenkinsHome = ((LocalController) jenkinsController).getJenkinsHome();
            File updatesDirectory = new File(jenkinsHome, "updates");
            FileUtils.copyFileToDirectory(
                resource("/hudson.plugins.gradle.GradleInstaller").asFile(), updatesDirectory);
            FileUtils.copyFileToDirectory(
                resource("/hudson.tasks.Maven.MavenInstaller").asFile(), updatesDirectory);
        }
    }

    protected final void addGlobalEnvironmentVariables(String... variables) {
        Preconditions.checkArgument(
            isEven(ArrayUtils.getLength(variables)),
            "variables array must have an even length");

        EnvironmentVariablesSettings settings = new EnvironmentVariablesSettings(jenkins);
        settings.configure();
        settings.clickEnvironmentVariables();

        for (List<String> pair : Iterables.partition(Arrays.asList(variables), 2)) {
            settings.addEnvironmentVariable(pair.get(0), pair.get(1));
        }

        settings.save();
    }

    private static boolean isEven(int number) {
        return (number & 1) == 0;
    }
}
