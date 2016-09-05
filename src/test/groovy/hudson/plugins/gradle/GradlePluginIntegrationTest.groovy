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
import spock.lang.Unroll

import static org.jvnet.hudson.test.JenkinsRule.getLog
/**
 * Tests for the Gradle build step.
 */
class GradlePluginIntegrationTest extends Specification {
    private final JenkinsRule j = new JenkinsRule()
    private final GradleInstallationRule gradleInstallationRule = new GradleInstallationRule(j)
    @Rule
    public final RuleChain rules = RuleChain.outerRule(j).around(gradleInstallationRule)

    def 'run the default tasks'() {
        given:
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = j.createFreeStyleProject()
        p.getBuildersList().add(new CreateFileBuilder("build.gradle", "defaultTasks 'hello'\ntask hello << { println 'Hello' }"))
        p.getBuildersList().add(gradle())

        when:
        FreeStyleBuild build = j.buildAndAssertSuccess(p)

        then:
        getLog(build).contains "Hello"
    }

    public Gradle gradle(Map options = [:]) {
        new Gradle(null, null, (String) options.tasks, (String) options.rootBuildScriptDir, (String) options.buildFile, gradleInstallationRule.getGradleVersion(), options.useWrapper ?: false, false,
                options.fromRootBuildScriptDir ?: false, true, false)
    }

    def 'run a task'() {
        given:
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = j.createFreeStyleProject()
        p.getBuildersList().add(new CreateFileBuilder("build.gradle", "task hello << { println 'Hello' }"))
        p.getBuildersList().add(gradle(tasks: 'hello'))

        when:
        FreeStyleBuild build = j.buildAndAssertSuccess(p)

        then:
        getLog(build).contains "Hello"
    }

    def 'build file in different directory'() {
        given:
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = j.createFreeStyleProject()
        p.getBuildersList().add(new CreateFileBuilder("build/build.gradle", "task hello << { println 'Hello' }"))
        p.getBuildersList().add(gradle(tasks: 'hello', buildFile: 'build/build.gradle'))

        when:
        FreeStyleBuild build = j.buildAndAssertSuccess(p)

        then:
        getLog(build).contains "Hello"
    }

    def 'build scan is discovered'() {
        given:
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = j.createFreeStyleProject()
        p.getBuildersList().add(new CreateFileBuilder("build.gradle", """
plugins {
    id 'com.gradle.build-scan' version '1.0'
}

buildScan {
    licenseAgreementUrl = 'https://gradle.com/terms-of-service'
    licenseAgree = 'yes'
}

task hello << { println 'Hello' }"""))
        p.getBuildersList().add(gradle(switches: '-Dscan', tasks: 'hello'))

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        def action = build.getAction(BuildScanAction)
        action.scanUrl.contains('gradle.com')
        new URL(action.scanUrl)
    }

    def 'wrapper in base dir'() {
        given:
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = j.createFreeStyleProject()
        p.getBuildersList().add(new CreateFileBuilder("build.gradle", "task hello << { println 'Hello' }"))
        p.getBuildersList().add(gradle(tasks: 'wrapper'))
        p.getBuildersList().add(gradle(useWrapper: true, tasks: 'hello'))

        expect:
        j.buildAndAssertSuccess(p)
    }

    @Unroll
    def 'wrapper in #wrapperDirDescription, build file #buildFile, #description'() {
        given:
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = j.createFreeStyleProject()
        p.getBuildersList().add(new CreateFileBuilder(buildFile, "task hello << { println 'Hello' }"))
        p.getBuildersList().add(gradle(tasks: 'wrapper', rootBuildScriptDir: wrapperDir))
        p.getBuildersList().add(gradle(
                [useWrapper: true, tasks: 'hello'] + settings))

        expect:
        j.buildAndAssertSuccess(p)

        where:
        buildFile                 | wrapperDir | settings
        'build/build.gradle'      | 'build'    | [rootBuildScriptDir: 'build', fromRootBuildScriptDir: true]
        'build/build.gradle'      | 'build'    | [rootBuildScriptDir: 'build', buildFile: 'build.gradle', fromRootBuildScriptDir: true]
        'build/build.gradle'      | 'build'    | [buildFile: 'build/build.gradle', fromRootBuildScriptDir: false]
        'build/build.gradle'      | 'build'    | [buildFile: 'build/build.gradle', fromRootBuildScriptDir: true]
        'build/some/build.gradle' | 'build'    | [rootBuildScriptDir: 'build', buildFile: 'some/build.gradle',  fromRootBuildScriptDir: true]
        'build/build.gradle'      | null       | [rootBuildScriptDir: 'build', fromRootBuildScriptDir: false]
        'build/build.gradle'      | null       | [rootBuildScriptDir: 'build', buildFile: 'build.gradle', fromRootBuildScriptDir: false]
        'build/build.gradle'      | null       | [buildFile: 'build/build.gradle', fromRootBuildScriptDir: false]
        'build/build.gradle'      | null       | [buildFile: 'build/build.gradle', fromRootBuildScriptDir: true]

        description = "configuration with buildScriptDir '${settings.rootBuildScriptDir}', ${settings.buildFile ?: ""} and the wrapper " +
                "from the ${settings.fromRootBuildScriptDir ? "buildScriptDir" : "workspace root"}"
        wrapperDirDescription = wrapperDir ?: 'workspace root'
    }

    def "Config roundtrip"() {
        given:
        gradleInstallationRule.addInstallation()
        def before = configuredGradle()

        when:
        def after = j.configRoundtrip(before)

        then:
        before.switches == after.switches
        before.tasks == after.tasks
        before.rootBuildScriptDir == after.rootBuildScriptDir
        before.buildFile == after.buildFile
        before.gradleName == after.gradleName
        before.useWrapper == after.useWrapper
        before.makeExecutable == after.makeExecutable
        before.wrapperScript == after.wrapperScript
        before.useWorkspaceAsHome == after.useWorkspaceAsHome
        before.passAsProperties == after.passAsProperties
    }

    Gradle gradle(Map options = [:]) {
        new Gradle(options.switches, options.tasks, null, options.buildFile, gradleInstallationRule.getGradleVersion(), false, false, null, true, false)
    }

    private Gradle configuredGradle() {
        new Gradle("switches", 'tasks', "buildScriptDir",
                "buildFile.gradle", gradleInstallationRule.gradleVersion, true, true, 'path/to/wrapper', true, true)
    }

    def 'add Gradle installation'() {
        given:
        String pagePath = j.jenkins.getVersion() >= (new VersionNumber("2.0")) ? "configureTools" : "configure"
        WebClient wc = j.createWebClient()
        HtmlPage p = wc.goTo(pagePath)
        HtmlForm f = p.getFormByName("config")
        HtmlButton b = j.getButtonByCaption(f, "Add Gradle")

        when:
        b.click()
        j.findPreviousInputElement(b, "name").setValueAttribute("myGradle")
        j.findPreviousInputElement(b, "home").setValueAttribute("/tmp/foo")
        j.submit(f)

        then:
        installationConfigured()

        // another submission and verify it survives a roundtrip
        when:
        p = wc.goTo(pagePath)
        f = p.getFormByName("config")
        j.submit(f)

        then:
        installationConfigured()
    }

    private void installationConfigured() {
        GradleInstallation[] installations = j.get(Gradle.DescriptorImpl).getInstallations()
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
