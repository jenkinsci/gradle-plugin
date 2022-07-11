package hudson.plugins.gradle.injection

import hudson.EnvVars
import hudson.model.Label
import hudson.plugins.gradle.AbstractIntegrationTest
import hudson.slaves.DumbSlave
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.slaves.NodeProperty
import hudson.tasks.Maven
import jenkins.model.Jenkins
import jenkins.mvn.DefaultGlobalSettingsProvider
import jenkins.mvn.DefaultSettingsProvider
import jenkins.mvn.GlobalMavenConfig
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.ToolInstallations

class BuildScanInjectionMavenIntegrationTest extends AbstractIntegrationTest {

    def pomFile = '<?xml version="1.0" encoding="UTF-8"?><project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"><modelVersion>4.0.0</modelVersion><groupId>com.example</groupId><artifactId>my-pom</artifactId><version>0.1-SNAPSHOT</version><packaging>pom</packaging><name>my-pom</name><description>my-pom</description></project>'

    def 'build scan is published without GE plugin with simple pipeline'() {
        given:
        setupBuildInjection()
        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition(simplePipeline(), false))

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        def log = JenkinsRule.getLog(build)
        hasJarInMavenExt(log, 'gradle-enterprise-maven-extension')
        !hasJarInMavenExt(log, 'common-custom-user-data-maven-extension')
        hasBuildScanPublicationAttempt(log)
    }



    def 'build scan is published without GE plugin with Maven plugin'() {
        given:
        setupBuildInjection()
        def pipelineJob = j.createProject(WorkflowJob)
        String mavenInstallationName = setupMavenInstallation()

        pipelineJob.setDefinition(new CpsFlowDefinition("""
node {
   stage('Build') {
        node('foo') {
            withMaven(maven: '$mavenInstallationName') {
                writeFile file: 'pom.xml', text: '$pomFile'
                if (isUnix()) {
                    sh "env"
                    sh "mvn package -B"
                } else {
                    bat "set"
                    bat "mvn package -B"
                }
            }
        }
   }
}
""", false))

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        def log = JenkinsRule.getLog(build)
        hasJarInMavenExt(log, 'gradle-enterprise-maven-extension')
        !hasJarInMavenExt(log, 'common-custom-user-data-maven-extension')
        hasBuildScanPublicationAttempt(log)
    }

    def 'build scan is published with CCUD extension applied'() {
        given:
        addGlobalEnvVar('JENKINSGRADLEPLUGIN_CCUD_EXTENSION_VERSION', '1.10.1')
        setupBuildInjection()
        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition(simplePipeline(), false))

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        def log = JenkinsRule.getLog(build)
        hasJarInMavenExt(log, 'gradle-enterprise-maven-extension')
        hasJarInMavenExt(log, 'common-custom-user-data-maven-extension')
        hasBuildScanPublicationAttempt(log)
    }

    def 'build scan is not published when global MAVEN_OPTS is set'() {
        given:
        addGlobalEnvVar('MAVEN_OPTS', '-Dfoo=bar')
        setupBuildInjection()
        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition(simplePipeline(), false))

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        def log = JenkinsRule.getLog(build)
        log =~ /MAVEN_OPTS=.*-Dfoo=bar.*/
        !hasJarInMavenExt(log, 'gradle-enterprise-maven-extension')
        !hasBuildScanPublicationAttempt(log)
    }

    private String simplePipeline() {
        """
node {
   stage('Build') {
        node('foo') {
                writeFile file: 'pom.xml', text: '$pomFile'
                if (isUnix()) {
                    sh "env"
                    sh "mvn package -B"
                } else {
                    bat "set"
                    bat "mvn package -B"
                }
        }
   }
}
"""
    }

    private String setupMavenInstallation() {
        def mavenInstallation = ToolInstallations.configureMaven35()
        Jenkins.get().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation)
        def mavenInstallationName = mavenInstallation.getName()

        GlobalMavenConfig globalMavenConfig = j.get(GlobalMavenConfig.class)
        globalMavenConfig.setGlobalSettingsProvider(new DefaultGlobalSettingsProvider())
        globalMavenConfig.setSettingsProvider(new DefaultSettingsProvider())
        mavenInstallationName
    }

    private DumbSlave setupBuildInjection() {
        EnvVars env = addGlobalEnvVar('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_INJECTION', 'on')
        env = addGlobalEnvVar('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_EXTENSION_VERSION', '1.14.2')
        DumbSlave slave = j.createOnlineSlave(Label.get("foo"), env)
        slave
    }

    private EnvVars addGlobalEnvVar(String key, String value) {
        NodeProperty nodeProperty = new EnvironmentVariablesNodeProperty()
        EnvVars env = nodeProperty.getEnvVars()
        env.put(key, value)
        j.jenkins.globalNodeProperties.add(nodeProperty)
        env
    }

    private static boolean hasJarInMavenExt(String log, String jar) {
        (log =~ /MAVEN_OPTS=.*-Dmaven\.ext\.class\.path=.*${jar}-.*\.jar/).find()
    }

    private static boolean hasBuildScanPublicationAttempt(String log) {
        (log =~ /The build scan was not published due to a configuration problem/).find()
    }

}
