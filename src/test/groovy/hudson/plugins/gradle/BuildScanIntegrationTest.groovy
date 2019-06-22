package hudson.plugins.gradle

import hudson.model.FreeStyleProject
import hudson.tasks.BatchFile
import hudson.tasks.Maven
import hudson.tasks.Shell
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jvnet.hudson.test.CreateFileBuilder
import org.jvnet.hudson.test.ExtractResourceSCM
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.ToolInstallations
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
        def action = build.getAction(BuildScanAction)
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
        p.buildWrappersList.add(new BuildScanBuildWrapper())
        p.buildersList.add(buildScriptBuilder('2.1'))
        p.buildersList.add(isUnix() ? new Shell('./gradlew --scan hello') : new BatchFile('gradlew.bat --scan hello'))

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        println JenkinsRule.getLog(build)
        def action = build.getAction(BuildScanAction)
        action.scanUrls.size() == 1
        new URL(action.scanUrls.get(0))
    }

    def 'detects build scan in pipeline log'() {
        given:
        gradleInstallationRule.gradleVersion = '5.1'
        gradleInstallationRule.addInstallation()
        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition("""
node {
   def gradleHome
   stage('Preparation') {
      gradleHome = tool '${gradleInstallationRule.gradleVersion}'
   }
   stage('Build') {
      // Run the maven build
      if (isUnix()) {
         sh 'touch settings.gradle'
         sh "'\${gradleHome}/bin/gradle' help --scan"
      } else {
         bat(/"\${gradleHome}\\bin\\gradle.bat" help --scan/)
      }
   }
   stage('Final') {
       findBuildScans()
   }
}
""", false))

        when:
        def build = pipelineJob.scheduleBuild2(0).get()

        then:
        println JenkinsRule.getLog(build)
        def action = build.getAction(BuildScanAction)
        action.scanUrls.size() == 1
        new URL(action.scanUrls.get(0))
    }

    def 'build scan is discovered from Maven build'() {
        given:
        def p = j.createFreeStyleProject()
        p.buildWrappersList.add(new BuildScanBuildWrapper())
        p.buildersList.add(new CreateFileBuilder("pom.xml",
"""<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>hudson.plugins.gradle</groupId>
  <artifactId>maven-build-scan</artifactId>
  <packaging>jar</packaging>
  <version>1.0</version>

  <properties>
    <maven.compiler.source>1.8</maven.compiler.source>
    <maven.compiler.target>1.8</maven.compiler.target>
  </properties>

</project>"""))
        p.buildersList.add(new CreateFileBuilder(".mvn/extensions.xml", buildScanExtension))
        p.buildersList.add(new CreateFileBuilder(".mvn/gradle-enterprise.xml", gradleEnterpriseConfiguration))
        def mavenInstallation = ToolInstallations.configureMaven35()
        p.buildersList.add(new Maven("package", mavenInstallation.name, null, "", "", false, null, null))

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        println JenkinsRule.getLog(build)
        def action = build.getAction(BuildScanAction)
        action.scanUrls.size() == 1
        new URL(action.scanUrls.get(0))
    }

    def 'build scan action is exposed via rest API'() {
        given:
        gradleInstallationRule.gradleVersion = '3.4'
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = j.createFreeStyleProject()
        p.buildersList.add(buildScriptBuilder())
        p.buildersList.add(new Gradle(tasks: 'hello', gradleName: '3.4', switches: '-Dscan --no-daemon'))


        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        println JenkinsRule.getLog(build)

        def json = j.getJSON("${build.url}/api/json?tree=actions[*]")
        def scanUrls = json.getJSONObject().get('actions').get(1).get('scanUrls')
        scanUrls.size() == 1
        new URL(scanUrls.get(0))
    }

    private static String getGradleEnterpriseConfiguration() {
        """<gradleEnterprise
    xmlns="https://www.gradle.com/gradle-enterprise-maven" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="https://www.gradle.com/gradle-enterprise-maven https://www.gradle.com/schema/gradle-enterprise-maven.xsd">
  <buildScan>
    <publish>ALWAYS</publish>
    <termsOfService>
      <url>https://gradle.com/terms-of-service</url>
      <accept>true</accept>
    </termsOfService>
  </buildScan>
</gradleEnterprise>
"""
    }

    private static String getBuildScanExtension() {
        """<?xml version="1.0" encoding="UTF-8"?>
<extensions>
    <extension>
        <groupId>com.gradle</groupId>
        <artifactId>gradle-enterprise-maven-extension</artifactId>
        <version>1.0.2</version>
    </extension>
</extensions>
"""
    }

    private static CreateFileBuilder buildScriptBuilder(String buildScanVersion = '1.8') {
        return new CreateFileBuilder('build.gradle', """
plugins {
    id 'com.gradle.build-scan' version '${buildScanVersion}'
}

buildScan {
    ${buildScanVersion.startsWith('2') ? 'termsOfServiceUrl' : 'licenseAgreementUrl'} = 'https://gradle.com/terms-of-service'
    ${buildScanVersion.startsWith('2') ? 'termsOfServiceAgree' : 'licenseAgree'} = 'yes'
}

task hello { doLast { println 'Hello' } }""")
    }

    private static boolean isUnix() {
        return File.pathSeparatorChar == ':' as char
    }
}
