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

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.ToolInstallations;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.StringWriter;

import static org.junit.Assert.assertTrue;

/**
 * Tests the withGradle pipeline step
 *
 * @author Alex Johnson
 */
public class WithGradleWorkFlowTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private String build_gradle = "writeFile(file:'build.gradle', text:'defaultTasks \\\'hello\\\'\\ntask hello << { println \\\'Hello\\\' }') \n";

    @Test
    public void testStepDefaultTools() throws Exception {
        String name = ToolInstallations.configureDefaultGradle(folder).getName();
        WorkflowJob p1 = j.jenkins.createProject(WorkflowJob.class, "FakeProject");
        p1.setDefinition(new CpsFlowDefinition("node {\n" + build_gradle +
                "withGradle(gradleName:'" + name + "'){\n" +
                "if (isUnix()) {\n" + 
                "sh 'gradle'\n" + // runs default task
                "} else {\n" +
                "bat 'gradle'\n" +
                "}\n" +
                "}\n" +
                "}", false));
        WorkflowRun r = p1.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(r);
    }

    @Test
    public void testGradleErrorFailsBuild() throws Exception {
        String name = ToolInstallations.configureDefaultGradle(folder).getName();
        WorkflowJob p1 = j.jenkins.createProject(WorkflowJob.class, "FakeProject");
        p1.setDefinition(new CpsFlowDefinition("node {\n" + build_gradle +
                "withGradle(gradleName:'" + name + "'){\n" +
                "if (isUnix()) {\n" + 
                "sh 'gradle unknownTask'\n" +
                "} else {\n" +
                "bat 'gradle unknownTask'\n" +
                "}\n" +
                "}\n" +
                "}", false));
        WorkflowRun r = p1.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, r);
    }

    @Test
    public void testStepWithConfiguredGradle() throws Exception {
        String name = ToolInstallations.configureDefaultGradle(folder).getName();
        WorkflowJob p1 = j.jenkins.createProject(WorkflowJob.class, "FakeProject");
        p1.setDefinition(new CpsFlowDefinition("node {\n" + build_gradle +
                "withGradle(gradleName:'" + name + "') {\n" +
                "if (isUnix()) {\n" + 
                "sh 'gradle -v'\n" +
                "sh 'gradle'\n" +
                "} else {\n" +
                "bat 'gradle -v'\n" +
                "bat 'gradle'\n" +
                "}\n" +
                "}\n" +
                "}", false));
        WorkflowRun r = p1.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(r);
        assertTrue(r.getLog().contains("Gradle 2.13")); // version bundled in jenkins-test-harness-tools
    }

    @Test
    public void testConsoleLogAnnotation () throws Exception {
        String name = ToolInstallations.configureDefaultGradle(folder).getName();
        WorkflowJob p1 = j.jenkins.createProject(WorkflowJob.class, "FakeProject");
        p1.setDefinition(new CpsFlowDefinition("node {\n" +
                "writeFile(file:'build.gradle', text:'defaultTasks \\\'bird\\\'\\ntask bird << { println \\\'chirp\\\' }') \n" +
                "withGradle(gradleName:'" + name + "') {\n" +
                "if (isUnix()) {\n" + 
                "sh 'gradle'\n" +
                "} else {\n" +
                "bat 'gradle'\n" +
                "}\n" +
                "}\n" +
                "}", false));
        WorkflowRun r = p1.scheduleBuild2(0).get();

        StringWriter log = new StringWriter();
        r.getLogText().writeHtmlTo(0, log);
        assertTrue(log.toString().contains("<b class=gradle-task>bird"));
    }
}
