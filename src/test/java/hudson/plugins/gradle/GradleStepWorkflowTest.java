/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.gradle;

import static org.junit.Assert.assertTrue;

import java.io.StringWriter;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.ToolInstallations;

import hudson.model.Result;

/**
 * Tests the Gradle Build step in an Jenkins Pipeline.
 *
 * @author Sönke Küper
 */
@SuppressWarnings("nls")
public class GradleStepWorkflowTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final String build_gradle = "writeFile(file:'build.gradle', text:'defaultTasks \\\'hello\\\'\\ntask hello << { println \\\'Hello\\\' }') \n";

    @Test
    public void testStepDefaultTools() throws Exception {
        final String name = ToolInstallations.configureDefaultGradle(this.folder).getName();
        final WorkflowJob p1 = this.j.jenkins.createProject(WorkflowJob.class, "FakeProject");
        p1.setDefinition(new CpsFlowDefinition(
            "node {\n" +
            this.build_gradle +
            "  gradle gradleName: '" + name + "'\n" +
            "}", false));
        final WorkflowRun r = p1.scheduleBuild2(0).get();
        this.j.assertBuildStatusSuccess(r);
    }

    @Test
    public void testGradleErrorFailsBuild() throws Exception {
        final String name = ToolInstallations.configureDefaultGradle(this.folder).getName();
        final WorkflowJob p1 = this.j.jenkins.createProject(WorkflowJob.class, "FakeProject");
        p1.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                this.build_gradle +
                "  gradle gradleName: '" + name + "', tasks: 'unknownTask'\n" +
                "}", false));
        final WorkflowRun r = p1.scheduleBuild2(0).get();
        this.j.assertBuildStatus(Result.FAILURE, r);
    }

    @Test
    public void testStepWithConfiguredGradle() throws Exception {
        final String name = ToolInstallations.configureDefaultGradle(this.folder).getName();
        final WorkflowJob p1 = this.j.jenkins.createProject(WorkflowJob.class, "FakeProject");
        p1.setDefinition(new CpsFlowDefinition(
            "node {\n" +
            this.build_gradle +
            "  gradle gradleName: '" + name + "', switches: '-v'\n" +
            "}",
            false));
        final WorkflowRun r = p1.scheduleBuild2(0).get();
        this.j.assertBuildStatusSuccess(r);
        assertTrue(r.getLog(), r.getLog().contains("Gradle 2.13")); // version bundled in jenkins-test-harness-tools
    }

    @Test
    public void testConsoleLogAnnotation () throws Exception {
        final String name = ToolInstallations.configureDefaultGradle(this.folder).getName();
        final WorkflowJob p1 = this.j.jenkins.createProject(WorkflowJob.class, "FakeProject");
        p1.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "writeFile(file:'build.gradle', text:'defaultTasks \\\'bird\\\'\\ntask bird << { println \\\'chirp\\\' }') \n" +
                "  gradle gradleName: '" + name + "'\n" +
                "}", false));
        final WorkflowRun r = p1.scheduleBuild2(0).get();

        final StringWriter log = new StringWriter();
        r.getLogText().writeHtmlTo(0, log);
        assertTrue(log.toString(), log.toString().contains("<b class=gradle-task>bird"));
    }
}
