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
import com.google.common.base.Joiner
import hudson.model.Cause
import hudson.model.FreeStyleBuild
import hudson.model.FreeStyleProject
import hudson.model.ParametersAction
import hudson.model.ParametersDefinitionProperty
import hudson.model.Result
import hudson.model.TextParameterDefinition
import hudson.model.TextParameterValue
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
        p.buildersList.add(new CreateFileBuilder("build.gradle", "defaultTasks 'hello'\ntask hello << { println 'Hello' }"))
        p.buildersList.add(new Gradle(defaults))

        when:
        FreeStyleBuild build = j.buildAndAssertSuccess(p)

        then:
        getLog(build).contains "Hello"
    }

    def 'run a task'() {
        given:
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = j.createFreeStyleProject()
        p.buildersList.add(new CreateFileBuilder("build.gradle", "task hello << { println 'Hello' }"))
        p.buildersList.add(new Gradle(tasks: 'hello', *:defaults))

        when:
        FreeStyleBuild build = j.buildAndAssertSuccess(p)

        then:
        getLog(build).contains "Hello"
    }

    def 'build file in different directory'() {
        given:
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = j.createFreeStyleProject()
        p.buildersList.add(new CreateFileBuilder("build/build.gradle", "task hello << { println 'Hello' }"))
        p.buildersList.add(new Gradle(tasks: 'hello', buildFile: 'build/build.gradle', *:defaults))

        when:
        FreeStyleBuild build = j.buildAndAssertSuccess(p)

        then:
        getLog(build).contains "Hello"
    }

    def 'build scan is discovered'() {
        given:
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = j.createFreeStyleProject()
        p.buildersList.add(new CreateFileBuilder("build.gradle", """
plugins {
    id 'com.gradle.build-scan' version '1.0'
}

buildScan {
    licenseAgreementUrl = 'https://gradle.com/terms-of-service'
    licenseAgree = 'yes'
}

task hello << { println 'Hello' }"""))
        p.buildersList.add(new Gradle(switches: '-Dscan', tasks: 'hello', *:defaults))

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
        p.buildersList.add(new CreateFileBuilder("build.gradle", "task hello << { println 'Hello' }"))
        p.buildersList.add(new Gradle(tasks: 'wrapper', *:defaults))
        p.buildersList.add(new Gradle(useWrapper: true, tasks: 'hello', *:defaults))

        expect:
        j.buildAndAssertSuccess(p)
    }

    @Unroll
    def 'wrapper in #wrapperDirDescription, build file #buildFile, #description'() {
        given:
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = j.createFreeStyleProject()
        p.buildersList.add(new CreateFileBuilder(buildFile, "task hello << { println 'Hello' }"))
        p.buildersList.add(new Gradle(tasks: 'wrapper', rootBuildScriptDir: wrapperDir, *:defaults))
        p.buildersList.add(new Gradle(
                defaults + [useWrapper: true, tasks: 'hello'] + settings))

        expect:
        j.buildAndAssertSuccess(p)

        where:
        buildFile                 | wrapperDir   | settings
        'build/build.gradle'      | 'build'      | [rootBuildScriptDir: 'build', wrapperLocation: 'build']
        'build/build.gradle'      | 'build'      | [rootBuildScriptDir: 'build', buildFile: 'build.gradle', wrapperLocation: 'build']
        'build/build.gradle'      | 'build'      | [buildFile: 'build/build.gradle']
        'build/some/build.gradle' | 'build'      | [rootBuildScriptDir: 'build', buildFile: 'some/build.gradle', wrapperLocation: 'build']
        'build/some/build.gradle' | 'build/some' | [rootBuildScriptDir: 'build', buildFile: 'some/build.gradle']
        'build/build.gradle'      | null         | [rootBuildScriptDir: 'build']
        'build/build.gradle'      | null         | [rootBuildScriptDir: 'build', buildFile: 'build.gradle']
        'build/build.gradle'      | null         | [buildFile: 'build/build.gradle']

        description = "configuration with buildScriptDir '${settings.rootBuildScriptDir}', ${settings.buildFile ?: ""} and the wrapper " +
                "from ${settings.wrapperLocation ?: "workspace root"}"
        wrapperDirDescription = wrapperDir ?: 'workspace root'
    }

    def 'wrapper was not found'() {
        given:
        FreeStyleProject p = j.createFreeStyleProject()
        p.buildersList.add(new CreateFileBuilder(buildFile, "task hello << { println 'Hello' }"))
        p.buildersList.add(new Gradle(defaults + [useWrapper: true, tasks: 'hello'] + settings))

        when:
        def build = p.scheduleBuild2(0).get()
        then:
        j.assertBuildStatus(Result.FAILURE, build);
        getLog(build).contains("The Gradle wrapper has not been found in these directories: ${searchedDirs.collect { Joiner.on('/').skipNulls().join(build.getWorkspace(), it) }.join(', ')}")

        where:
        buildFile                 | settings                                                                                    | searchedDirs
        'build/build.gradle'      | [buildFile: 'build/build.gradle']                                                           | ['build', null]
        'build.gradle'            | [buildFile: 'build.gradle']                                                                 | [null]
        'build/some/build.gradle' | [rootBuildScriptDir: 'build', buildFile: 'some/build.gradle']                               | ['build/some', null]
        'build/build.gradle'      | [buildFile: 'build/build.gradle']                                                           | ['build', null]
        'build.gradle'            | [buildFile: 'build.gradle']                                                                 | [null]
        'build/some/build.gradle' | [wrapperLocation: 'somewhere', rootBuildScriptDir: 'build', buildFile: 'some/build.gradle'] | ['somewhere']
        'build/some/build.gradle' | [rootBuildScriptDir: 'build']                                                               | [null]
    }

    def "Can use > signs in system properties"() {
        given:
        gradleInstallationRule.addInstallation()
        def p = j.createFreeStyleProject()
        p.addProperty(new ParametersDefinitionProperty(new TextParameterDefinition('PARAM', null, null)))
        p.buildersList.add(new CreateFileBuilder("build.gradle", "task printParam { doLast { println 'property=' + System.getProperty('PARAM') } }"))
        p.buildersList.add(new Gradle(tasks: 'wrapper', *:defaults))
        p.buildersList.add(new Gradle(tasks: 'printParam', useWrapper: true, useWorkspaceAsHome: true))

        when:
        def build = j.assertBuildStatusSuccess(p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new TextParameterValue("PARAM", "a < b"))))

        then:
        // TODO: Make this return 'property=a < b'
        getLog(build).contains('property="a < b"')
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
        before.wrapperLocation == after.wrapperLocation
        before.useWorkspaceAsHome == after.useWorkspaceAsHome
        before.passAsProperties == after.passAsProperties
    }

    private Gradle configuredGradle() {
        new Gradle(switches: "switches", tasks: 'tasks', rootBuildScriptDir: "buildScriptDir",
                buildFile:  "buildFile.gradle", gradleName:  gradleInstallationRule.gradleVersion,
                useWrapper: true, makeExecutable: true, wrapperLocation: 'path/to/wrapper',
                useWorkspaceAsHome: true, passAsProperties: true)
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

    Map getDefaults() {
        [gradleName: gradleInstallationRule.gradleVersion, useWorkspaceAsHome: true]
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
