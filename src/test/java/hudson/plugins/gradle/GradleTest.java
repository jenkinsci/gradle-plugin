/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolProperty;
import hudson.tools.ToolPropertyDescriptor;
import hudson.util.DescribableList;
import hudson.util.VersionNumber;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.CreateFileBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.ToolInstallations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests for the Gradle build step.
 */
public class GradleTest {
    @Rule
    public final JenkinsRule rule = new JenkinsRule();
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void buildStepDefaultTask() throws Exception {
        final GradleInstallation gi = ToolInstallations.configureDefaultGradle(tmp);
        FreeStyleProject p = rule.createFreeStyleProject();
        p.getBuildersList().add(new CreateFileBuilder("build.gradle", "defaultTasks 'hello'\ntask hello << { println 'Hello' }"));
        p.getBuildersList().add(new Gradle(null, null, null, null, null, gi.getName(), false, false, false, false, false));
        FreeStyleBuild build = rule.buildAndAssertSuccess(p);
        rule.assertLogContains("Hello", build);
    }

    @Test
    public void buildStepCustomTask() throws Exception {
        final GradleInstallation gi = ToolInstallations.configureDefaultGradle(tmp);
        FreeStyleProject p = rule.createFreeStyleProject();
        p.getBuildersList().add(new CreateFileBuilder("build.gradle", "task hello << { println 'Hello' }"));
        p.getBuildersList().add(new Gradle(null, null, "hello", null, null, gi.getName(), false, false, false, false, false));
        FreeStyleBuild build = rule.buildAndAssertSuccess(p);
        rule.assertLogContains("Hello", build);
    }

    @Test
    public void buildStepCustomDir() throws Exception {
        final GradleInstallation gi = ToolInstallations.configureDefaultGradle(tmp);
        FreeStyleProject p = rule.createFreeStyleProject();
        p.getBuildersList().add(new CreateFileBuilder("build/build.gradle", "task hello << { println 'Hello' }"));
        p.getBuildersList().add(new Gradle(null, null, "hello", "build", null, gi.getName(), false, false, false, false, false));
        FreeStyleBuild build = rule.buildAndAssertSuccess(p);
        rule.assertLogContains("Hello", build);
    }

    @Test
    public void testGlobalConfigAjax() throws Exception {
        final String pagePath = rule.jenkins.getVersion().compareTo(new VersionNumber("2.0")) >= 0 ?"configureTools" : "configure";
        final WebClient wc = rule.createWebClient();
        HtmlPage p = wc.goTo(pagePath);
        HtmlForm f = p.getFormByName("config");
        HtmlButton b = rule.getButtonByCaption(f, "Add Gradle");
        b.click();
        rule.findPreviousInputElement(b,"name").setValueAttribute("myGradle");
        rule.findPreviousInputElement(b,"home").setValueAttribute("/tmp/foo");
        rule.submit(f);
        verify();

        // another submission and verify it survives a roundtrip
        p = wc.goTo(pagePath);
        f = p.getFormByName("config");
        rule.submit(f);
        verify();
    }

    private void verify() throws Exception {
        GradleInstallation[] l = rule.get(Gradle.DescriptorImpl.class).getInstallations();
        assertEquals(1,l.length);
        rule.assertEqualBeans(l[0],new GradleInstallation("myGradle","/tmp/foo", JenkinsRule.NO_PROPERTIES),"name,home");

        // by default we should get the auto installer
        DescribableList<ToolProperty<?>,ToolPropertyDescriptor> props = l[0].getProperties();
        assertEquals(1,props.size());
        InstallSourceProperty isp = props.get(InstallSourceProperty.class);
        assertEquals(1,isp.installers.size());
        assertNotNull(isp.installers.get(GradleInstaller.class));
    }

}
