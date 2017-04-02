package hudson.plugins.gradle

import hudson.model.FreeStyleProject
import org.jvnet.hudson.test.CreateFileBuilder

class BuildScanIntegrationTest extends AbstractIntegrationTest {
    def 'build scan is discovered'() {
        given:
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = j.createFreeStyleProject()
        p.buildersList.add(new CreateFileBuilder("build.gradle", """
plugins {
    id 'com.gradle.build-scan' version '1.6'
}

buildScan {
    licenseAgreementUrl = 'https://gradle.com/terms-of-service'
    licenseAgree = 'yes'
}

task hello << { println 'Hello' }"""))
        p.buildersList.add(new Gradle(tasks: 'hello', *: defaults, switches: '-Dscan --no-daemon'))

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        def action = build.getAction(BuildScanAction)
        action.scanUrl.contains('gradle.com')
        new URL(action.scanUrl)
    }
}
