package hudson.plugins.gradle

import hudson.model.FreeStyleProject
import hudson.tasks.Shell
import org.jvnet.hudson.test.CreateFileBuilder
import org.jvnet.hudson.test.ExtractResourceSCM
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Unroll

@Unroll
class BuildScanIntegrationTest extends AbstractIntegrationTest {

    def 'build scans for plugin version #buildScanVersion is discovered'() {
        given:
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = j.createFreeStyleProject()
        p.buildersList.add(buildScriptBuilder(buildScanVersion))
        p.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "${args} --no-daemon"))
        p.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "${args} --no-daemon"))

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        println JenkinsRule.getLog(build)
        def action = build.getAction(BuildScansAction)
        action.scanUrls.size() == 2
        action.scanUrls.each { new URL(it) }

        where:
        buildScanVersion | gradleVersion | args
        "1.6"            | "3.4"         | "-Dscan"
        "1.8"            | "4.0"         | "--scan"
    }

    def 'build scan is discovered when using non-gradle build step'() {
        given:
        FreeStyleProject p = j.createFreeStyleProject()
        p.setScm(new ExtractResourceSCM(this.class.getResource('/gradle/wrapper.zip')))
        p.buildersList.add(buildScriptBuilder())
        p.buildersList.add(new Shell(isUnix() ? './gradlew --scan hello' : 'cmd /c "gradlew.bat --scan hello"'))

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        println JenkinsRule.getLog(build)
        def action = build.getAction(BuildScansAction)
        action.scanUrls.size() == 1
        new URL(action.scanUrls.get(0))
    }

    private static CreateFileBuilder buildScriptBuilder(String buildScanVersion = '1.8') {
        return new CreateFileBuilder('build.gradle', """
plugins {
    id 'com.gradle.build-scan' version '${buildScanVersion}'
}

buildScan {
    licenseAgreementUrl = 'https://gradle.com/terms-of-service'
    licenseAgree = 'yes'
}

task hello { doLast { println 'Hello' } }""")
    }

    private static boolean isUnix() {
        return File.pathSeparatorChar == ':' as char
    }
}
