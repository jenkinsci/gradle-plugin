package hudson.plugins.gradle

import hudson.model.FreeStyleProject
import org.jvnet.hudson.test.CreateFileBuilder
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Unroll

@Unroll
class BuildScanIntegrationTest extends AbstractIntegrationTest {
    
    def 'build scan for plugin version #buildScanVersion is discovered'() {
        given:
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = j.createFreeStyleProject()
        p.buildersList.add(new CreateFileBuilder("build.gradle", """
plugins {
    id 'com.gradle.build-scan' version '${buildScanVersion}'
}

buildScan {
    licenseAgreementUrl = 'https://gradle.com/terms-of-service'
    licenseAgree = 'yes'
}

task hello { doLast { println 'Hello' } }"""))
        p.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "${args} --no-daemon"))

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        println JenkinsRule.getLog(build)
        def action = build.getAction(BuildScanAction)
        action.scanUrl.contains('gradle.com')
        new URL(action.scanUrl)

        where:
        buildScanVersion | gradleVersion | args
        "1.6" | "3.4" | "-Dscan"
        "1.8" | "4.0" | "--scan"
    }
}
