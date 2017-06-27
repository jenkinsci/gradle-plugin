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

import hudson.model.JDK;
import hudson.model.Label;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.queue.QueueTaskFuture;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;
import java.util.List;
import java.util.Queue;

/**
 * Tests the withGradle pipeline step
 *
 * @author Alex Johnson
 */
public class WithGradleWorkFlowTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private String build_gradle = "writeFile(file:'build.gradle', text:'defaultTasks \\\'hello\\\'\\ntask hello << { println \\\'Hello\\\' }') \n";

    @Test
    public void testStepDefaultTools() throws Exception {
        WorkflowJob p1 = j.jenkins.createProject(WorkflowJob.class, "FakeProject");
        p1.setDefinition(new CpsFlowDefinition("node {\n" + build_gradle +
                "withGradle {\n" +
                "sh 'gradle'\n" + // runs default task
                "}\n" +
                "}", false));
        WorkflowRun r = p1.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(r);
        // TODO test Gradle ran correctly
    }

    //@Test TODO unskip
    public void testGradleErrorFailsBuild() throws Exception {
        WorkflowJob p1 = j.jenkins.createProject(WorkflowJob.class, "FakeProject");
        p1.setDefinition(new CpsFlowDefinition("node {\n" + build_gradle +
                "withGradle {\n" +
                "sh 'gradle unknownTask'\n" +
                "}\n" +
                "}", false));
        WorkflowRun r = p1.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, r);
    }

    @Test
    public void testStepWithConfiguredGradle() throws Exception {
        WorkflowJob p1 = j.jenkins.createProject(WorkflowJob.class, "FakeProject");
        GradleInstallation g = new GradleInstallation("g2", "/no/such/home", Collections.EMPTY_LIST);
        ToolInstallation.all().add((ToolDescriptor) g.getDescriptor());
        p1.setDefinition(new CpsFlowDefinition("node {\n" + build_gradle +
                "withGradle (gradle: 'g2') {\n" +
                "sh 'gradle'\n" +
                "}\n" +
                "}", false));
        WorkflowRun r = p1.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(r);
        // TODO test Gradle ran correctly
    }

    @Test
    public void testStepWithConfiguredGradleAndJava() throws Exception {
        WorkflowJob p1 = j.jenkins.createProject(WorkflowJob.class, "FakeProject");
        GradleInstallation g = new GradleInstallation("g2", "/no/such/home", Collections.EMPTY_LIST);
        j.jenkins.setJDKs(Collections.singleton(new JDK("jaba", "/no/such/home")));
        ToolInstallation.all().add((ToolDescriptor) g.getDescriptor());
        p1.setDefinition(new CpsFlowDefinition("node {\n" + build_gradle +
                "withGradle (gradle: 'g2', jdk: 'jaba') {" +
                "sh 'gradle'\n" +
                "}\n" +
                "}", false));
        WorkflowRun r = p1.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(r);
        // TODO test Gradle ran correctly
    }
}
