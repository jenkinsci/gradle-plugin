package hudson.plugins.gradle.injection

import com.cloudbees.plugins.credentials.Credentials
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
import hudson.EnvVars
import hudson.plugins.gradle.AbstractIntegrationTest
import hudson.plugins.gradle.JavaGitContainer
import hudson.plugins.sshslaves.SSHLauncher
import hudson.slaves.DumbSlave
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.slaves.RetentionStrategy
import hudson.tasks.Maven
import jenkins.model.Jenkins
import jenkins.mvn.DefaultGlobalSettingsProvider
import jenkins.mvn.DefaultSettingsProvider
import jenkins.mvn.GlobalMavenConfig
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.test.acceptance.docker.DockerRule
import org.jenkinsci.test.acceptance.docker.fixtures.SshdContainer
import org.junit.Rule
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.ToolInstallations

class BuildScanInjectionMavenIntegrationTest extends AbstractIntegrationTest {

    private static final String SSH_CREDENTIALS_ID = "test";
    private static final String AGENT_NAME = "remote";
    private static final String SLAVE_BASE_PATH = "/home/test/slave";

    @Rule
    public DockerRule<JavaGitContainer> javaGitContainerRule = new DockerRule<>(JavaGitContainer.class);

    def 'build scan is published without GE plugin with pipeline withMaven'() {
        given:
        registerAgentForContainer(javaGitContainerRule.get())

        and:
        def pipelineJob = j.createProject(WorkflowJob)

        def mavenInstallation = ToolInstallations.configureMaven35()
        Jenkins.get().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation)
        def mavenInstallationName = mavenInstallation.getName()

        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty()
        EnvVars env = prop.getEnvVars()
        env.put("JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_EXTENSION_VERSION", "1.14.2")
        j.jenkins.getGlobalNodeProperties().add(prop)

        GlobalMavenConfig globalMavenConfig = j.get(GlobalMavenConfig.class);
        globalMavenConfig.setGlobalSettingsProvider(new DefaultGlobalSettingsProvider())
        globalMavenConfig.setSettingsProvider(new DefaultSettingsProvider())


        def pomFile = '''<?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>my-pom</artifactId>
                    <version>0.1-SNAPSHOT</version>
                    <packaging>pom</packaging>
                    <name>my-pom</name>
                    <description>my-pom</description>
                </project>'''

        pipelineJob.setDefinition(new CpsFlowDefinition("""
node {
   stage('Build') {
        node('$AGENT_NAME') {
            withMaven(maven: '$mavenInstallationName') {
                sh "env"
                sh '''cat <<EOT >> pom.xml
$pomFile
EOT'''
                sh "mvn package"
            }
        }
   }
}
""", false))

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        println JenkinsRule.getLog(build)
        j.assertLogContains('Publishing a build scan to scans.gradle.com ', build)
    }

    private void registerAgentForContainer(SshdContainer container) throws Exception {
        addTestSshCredentials();
        registerAgentForSlaveContainer(container);
    }

    private void registerAgentForSlaveContainer(SshdContainer slaveContainer) throws Exception {
        SSHLauncher sshLauncher = new SSHLauncher(slaveContainer.ipBound(22), slaveContainer.port(22), SSH_CREDENTIALS_ID);

        DumbSlave agent = new DumbSlave(AGENT_NAME, SLAVE_BASE_PATH, sshLauncher);
        agent.setNumExecutors(1);
        agent.setRetentionStrategy(RetentionStrategy.INSTANCE);

        j.jenkins.addNode(agent);
    }

    private void addTestSshCredentials() {
        Credentials credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, SSH_CREDENTIALS_ID, null, "test", "test");

        SystemCredentialsProvider.getInstance()
            .getDomainCredentialsMap()
            .put(Domain.global(), Collections.singletonList(credentials));
    }
}
