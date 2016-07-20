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

package hudson.plugins.gradle

import com.gargoylesoftware.htmlunit.html.HtmlButton
import com.gargoylesoftware.htmlunit.html.HtmlForm
import com.gargoylesoftware.htmlunit.html.HtmlPage
import hudson.model.FreeStyleBuild
import hudson.model.FreeStyleProject
import hudson.tools.InstallSourceProperty
import hudson.util.VersionNumber
import org.junit.Rule
import org.junit.rules.RuleChain
import org.jvnet.hudson.test.CreateFileBuilder
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.JenkinsRule.WebClient
import spock.lang.Specification

import static org.jvnet.hudson.test.JenkinsRule.getLog
/**
 * Tests for the Gradle build step.
 */
class GradleTest extends Specification {
    private final JenkinsRule rule = new JenkinsRule()
    private final GradleInstallationRule gradleInstallationRule = new GradleInstallationRule(rule)
    @Rule
    public final RuleChain rules = RuleChain.outerRule(rule).around(gradleInstallationRule)

    def 'run the default tasks'() {
        given:
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = rule.createFreeStyleProject()
        p.getBuildersList().add(new CreateFileBuilder("build.gradle", "defaultTasks 'hello'\ntask hello << { println 'Hello' }"))
        p.getBuildersList().add(new Gradle(null, null, null, null, null, gradleInstallationRule.getGradleVersion(), false, false, false, false, false))

        when:
        FreeStyleBuild build = rule.buildAndAssertSuccess(p)

        then:
        getLog(build).contains "Hello"
    }

    def 'run a task'() {
        given:
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = rule.createFreeStyleProject()
        p.getBuildersList().add(new CreateFileBuilder("build.gradle", "task hello << { println 'Hello' }"))
        p.getBuildersList().add(new Gradle(null, null, "hello", null, null, gradleInstallationRule.getGradleVersion(), false, false, false, false, false))

        when:
        FreeStyleBuild build = rule.buildAndAssertSuccess(p)

        then:
        getLog(build).contains "Hello"
    }

    def 'build file in different directory'() {
        given:
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = rule.createFreeStyleProject()
        p.getBuildersList().add(new CreateFileBuilder("build/build.gradle", "task hello << { println 'Hello' }"))
        p.getBuildersList().add(new Gradle(null, null, "hello", "build", null, gradleInstallationRule.getGradleVersion(), false, false, false, false, false))

        when:
        FreeStyleBuild build = rule.buildAndAssertSuccess(p)

        then:
        getLog(build).contains "Hello"
    }

    def 'add Gradle installation'() {
        given:
        String pagePath = rule.jenkins.getVersion() >= (new VersionNumber("2.0")) ? "configureTools" : "configure"
        WebClient wc = rule.createWebClient()
        HtmlPage p = wc.goTo(pagePath)
        HtmlForm f = p.getFormByName("config")
        HtmlButton b = rule.getButtonByCaption(f, "Add Gradle")

        when:
        b.click()
        rule.findPreviousInputElement(b, "name").setValueAttribute("myGradle")
        rule.findPreviousInputElement(b, "home").setValueAttribute("/tmp/foo")
        rule.submit(f)

        then:
        installationConfigured()

        // another submission and verify it survives a roundtrip
        when:
        p = wc.goTo(pagePath)
        f = p.getFormByName("config")
        rule.submit(f)

        then:
        installationConfigured()
    }

    private void installationConfigured() {
        GradleInstallation[] installations = rule.get(Gradle.DescriptorImpl).getInstallations()
        assert installations.size() == 1
        def installation = installations[0]
        installation.name == 'myGradle'
        installation.home == '/tmp/foo'

        // by default we should get the auto installer
        def props = installations[0].getProperties()
        assert props.size() == 1
        def installers = props.get(InstallSourceProperty).installers
        assert installers.size() == 1
        assert installers.get(GradleInstaller)
    }
}
