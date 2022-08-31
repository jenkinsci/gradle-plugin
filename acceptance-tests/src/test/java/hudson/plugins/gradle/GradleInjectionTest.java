package hudson.plugins.gradle;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.text.StrSubstitutor;
import org.apache.commons.lang3.SystemUtils;
import org.codehaus.plexus.util.Base64;
import org.jenkinsci.test.acceptance.junit.Resource;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.gradle.GradleInstallation;
import org.jenkinsci.test.acceptance.plugins.gradle.GradleStep;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.WorkflowJob;
import org.junit.Before;
import org.junit.Test;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.jenkinsci.test.acceptance.Matchers.containsString;
import static org.junit.Assume.assumeTrue;

public class GradleInjectionTest extends AbstractAcceptanceTest {

    private static final String GRADLE_VERSION = "Gradle 7.5.1";

    @Before
    public void beforeEach() {
        GradleInstallation.installGradle(jenkins, GRADLE_VERSION, GRADLE_VERSION);

        addGlobalEnvironmentVariables(
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_INJECTION", "true",
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION", "3.11.1",
            "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL", mockGeServerAddress().toString()
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

        String output = build.getConsole();
        assertThat(output, containsString("Applying com.gradle.enterprise.gradleplugin.GradleEnterprisePlugin via init script"));
        assertThat(output, containsString("> Task :helloWorld"));
        assertThat(output, containsString("Hello, World!"));
        assertThat(output, containsString("Publishing build scan..." + System.lineSeparator() + publicBuildScanId()));

        // TODO: Check that build scan is visible on the page
    }

    @Test
    @WithPlugins("pipeline-model-definition")
    public void pipelineJobPublishesBuildScan() {
        // copyResourceDirStep doesn't support Windows
        assumeTrue(SystemUtils.IS_OS_UNIX);

        // given
        WorkflowJob job = jenkins.jobs.create(WorkflowJob.class);

        String pipelineTemplate = resource("/simple_gradle_project.groovy.template").asText();
        String pipeline =
            new StrSubstitutor(
                ImmutableMap.of(
                    "copy_resource_step", copyResourceDirStep(resource("/simple_gradle_project")),
                    "gradle_version", GRADLE_VERSION,
                    "tasks", "--no-daemon helloWorld"
                ))
                .replace(pipelineTemplate);

        job.script.set(pipeline);
        job.sandbox.check();
        job.save();

        // when
        Build build = job.startBuild();

        // then
        build.shouldSucceed();

        String output = build.getConsole();
        assertThat(output, containsString("Applying com.gradle.enterprise.gradleplugin.GradleEnterprisePlugin via init script"));
        assertThat(output, containsString("> Task :helloWorld"));
        assertThat(output, containsString("Hello, World!"));
        assertThat(output, containsString("Publishing build scan..." + System.lineSeparator() + publicBuildScanId()));
    }

    private static String copyResourceDirStep(Resource dir) {
        File file = dir.asFile();
        Preconditions.checkArgument(file.isDirectory(), "'%s' is not a directory", dir.getName());

        File tmp = null;
        try {
            tmp = File.createTempFile("jenkins-acceptance-tests", "dir");
            ZipUtil.pack(file, tmp);
            byte[] archive = IOUtils.toByteArray(Files.newInputStream(tmp.toPath()));

            String shell = String.format(
                "base64 --decode << ENDOFFILE > archive.zip && unzip -o archive.zip \n%s\nENDOFFILE",
                new String(Base64.encodeBase64Chunked(archive)));

            return String.format("sh '''%s'''%n", shell);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (tmp != null) {
                tmp.delete();
            }
        }
    }
}
