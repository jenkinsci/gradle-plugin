package hudson.plugins.gradle.injection

import hudson.FilePath
import hudson.plugins.git.BranchSpec
import hudson.plugins.git.GitSCM
import hudson.plugins.git.extensions.GitSCMExtension
import hudson.plugins.gradle.BaseJenkinsIntegrationTest
import hudson.plugins.gradle.BuildScanBuildWrapper
import hudson.slaves.DumbSlave
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.tasks.Maven
import hudson.util.Secret
import jenkins.model.Jenkins
import jenkins.mvn.DefaultGlobalSettingsProvider
import jenkins.mvn.DefaultSettingsProvider
import jenkins.mvn.GlobalMavenConfig
import org.apache.commons.lang3.StringUtils
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.ToolInstallations
import spock.lang.Issue
import spock.lang.Unroll

class BuildScanInjectionMavenIntegrationTest extends BaseJenkinsIntegrationTest {

    private static final String GE_EXTENSION_JAR = "gradle-enterprise-maven-extension.jar"
    private static final String CCUD_EXTENSION_JAR = "common-custom-user-data-maven-extension.jar"
    private static final String CONFIGURATION_EXTENSION_JAR = "configuration-maven-extension.jar"

    private static final List<String> ALL_EXTENSIONS = [GE_EXTENSION_JAR, CCUD_EXTENSION_JAR, CONFIGURATION_EXTENSION_JAR]

    private static final String POM_XML = '<?xml version="1.0" encoding="UTF-8"?><project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"><modelVersion>4.0.0</modelVersion><groupId>com.example</groupId><artifactId>my-pom</artifactId><version>0.1-SNAPSHOT</version><packaging>pom</packaging><name>my-pom</name><description>my-pom</description></project>'

    @Unroll
    def 'does not copy #extension if it was not changed'() {
        when:
        def slave = createSlaveAndTurnOnInjection()
        turnOnBuildInjectionAndRestart(slave)
        def extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 3

        def originalExtension = extensionDirectory.list().find { it.name == extension }
        originalExtension != null
        def originalExtensionLastModified = originalExtension.lastModified()
        originalExtensionLastModified > 0
        def originalExtensionDigest = originalExtension.digest()
        originalExtensionDigest != null

        when:
        restartSlave(slave)

        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 3

        def updatedExtension = extensionDirectory.list().find { it.name == extension }
        updatedExtension != null
        updatedExtension.lastModified() == originalExtensionLastModified
        updatedExtension.digest() == originalExtensionDigest

        where:
        extension << ALL_EXTENSIONS
    }

    @Unroll
    def 'copies a new version of #extension if it was changed'() {
        when:
        def slave = createSlaveAndTurnOnInjection()
        turnOnBuildInjectionAndRestart(slave)
        def extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 3

        def originalExtension = extensionDirectory.list().find { it.name == extension }
        originalExtension != null
        def originalExtensionLastModified = originalExtension.lastModified()
        originalExtensionLastModified > 0
        def originalExtensionDigest = originalExtension.digest()
        originalExtensionDigest != null

        when:
        def random = new Random()

        def extensionRandomBytes = new byte[10]
        random.nextBytes(extensionRandomBytes)
        originalExtension.copyFrom(new ByteArrayInputStream(extensionRandomBytes))

        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().find { it.name == extension }?.lastModified() != originalExtensionLastModified

        when:
        restartSlave(slave)

        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        def updatedGeExtension = extensionDirectory.list().find { it.name == extension }
        updatedGeExtension != null
        updatedGeExtension.lastModified() != originalExtensionLastModified
        updatedGeExtension.digest() == originalExtensionDigest

        where:
        extension << ALL_EXTENSIONS
    }

    @Issue('https://issues.jenkins.io/browse/JENKINS-70663')
    def 'does not add an empty MAVEN_OPTS if auto-injection is disabled'() {
        when:
        def agent = createSlave('test')

        then:
        noMavenOpts(agent)
    }

    @Issue('https://issues.jenkins.io/browse/JENKINS-70692')
    def 'does not modify existing MAVEN_OPTS if auto-injection is disabled'() {
        given:
        def mavenOpts = '-Dmaven.ext.class.path=/tmp/custom-extension.jar'
        def agent = createSlave('test')

        withNodeEnvVars(agent) {
            put('MAVEN_OPTS', mavenOpts)
        }

        when:
        restartSlave(agent)

        then:
        getMavenOptsFromNodeProperties(agent) == mavenOpts
    }

    def 'delete MAVEN_OPTS and maven-plugin variables set by older versions if auto-injection is disabled'() {
        given:
        def mavenOpts = '-Dmaven.ext.class.path=temp/jenkins-gradle-plugin/lib/gradle-enterprise-maven-extension.jar:temp/jenkins-gradle-plugin/lib/common-custom-user-data-maven-extension.jar -Dgradle.scan.uploadInBackground=false -Dgradle.enterprise.url=https://scans.gradle.com -Dgradle.enterprise.allowUntrustedServer=true'
        def agent = createSlave('foo')

        withNodeEnvVars(agent) {
            put(MavenOptsHandler.MAVEN_OPTS, mavenOpts)
            put(MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL, 'http://locahost')
            put(MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER, 'true')
            put(MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH, '-Dmaven.ext.class.path=/tmp/custom-extension.jar')
        }

        withInjectionConfig {
            enabled = true
            server = 'https://scans.gradle.com'
            injectMavenExtension = true
        }

        when:
        restartSlave(agent)

        then:
        getMavenOptsFromNodeProperties(agent) == mavenOpts
        getEnvVarFromNodeProperties(agent, MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL) == 'http://locahost'
        getEnvVarFromNodeProperties(agent, MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER) == 'true'
        getEnvVarFromNodeProperties(agent, MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH) == '-Dmaven.ext.class.path=/tmp/custom-extension.jar'

        when:
        withInjectionConfig {
            enabled = false
            server = null
            injectMavenExtension = false
        }

        restartSlave(agent)

        then:
        getMavenOptsFromNodeProperties(agent) == null
        getEnvVarFromNodeProperties(agent, MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL) == null
        getEnvVarFromNodeProperties(agent, MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER) == null
        getEnvVarFromNodeProperties(agent, MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH) == null
    }

    def 'does not create new EnvironmentVariablesNodeProperty when MAVEN_OPTS changes'() {
        when:
        def slave = createSlaveAndTurnOnInjection()

        then:
        slave.getNodeProperties().getAll(EnvironmentVariablesNodeProperty.class).size() == 1

        FilePath extensionDirectoryRequired = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)
        extensionDirectoryRequired.list().size() == 2
        extensionDirectoryRequired.list().find { it.name == GE_EXTENSION_JAR } != null
        extensionDirectoryRequired.list().find { it.name == CONFIGURATION_EXTENSION_JAR } != null
        extensionDirectoryRequired.list().find { it.name == CCUD_EXTENSION_JAR } == null

        when:
        turnOnBuildInjectionAndRestart(slave)

        then:
        slave.getNodeProperties().getAll(EnvironmentVariablesNodeProperty.class).size() == 1

        FilePath extensionDirectoryAll = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)
        extensionDirectoryAll.list().size() == 3
        extensionDirectoryAll.list().find { it.name == GE_EXTENSION_JAR } != null
        extensionDirectoryAll.list().find { it.name == CONFIGURATION_EXTENSION_JAR } != null
        extensionDirectoryAll.list().find { it.name == CCUD_EXTENSION_JAR } != null

        when:
        turnOffBuildInjectionAndRestart(slave)

        then:
        slave.getNodeProperties().getAll(EnvironmentVariablesNodeProperty.class).size() == 1

        FilePath extensionDirectoryEmpty = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)
        extensionDirectoryEmpty.list().size() == 0
        extensionDirectoryEmpty.list().find { it.name == GE_EXTENSION_JAR } == null
        extensionDirectoryEmpty.list().find { it.name == CONFIGURATION_EXTENSION_JAR } == null
        extensionDirectoryEmpty.list().find { it.name == CCUD_EXTENSION_JAR } == null

        noMavenOpts(slave)
    }

    def 'build scan is published without GE plugin with simple pipeline'() {
        given:
        def slave = createSlaveAndTurnOnInjection()
        def mavenInstallationName = setupMavenInstallation()

        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition(simplePipeline(mavenInstallationName), false))

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        def log = JenkinsRule.getLog(build)
        FilePath extensionDirectoryRequired = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)
        extensionDirectoryRequired.list().size() == 2
        extensionDirectoryRequired.list().find { it.name == GE_EXTENSION_JAR } != null
        extensionDirectoryRequired.list().find { it.name == CONFIGURATION_EXTENSION_JAR } != null
        extensionDirectoryRequired.list().find { it.name == CCUD_EXTENSION_JAR } == null

        hasBuildScanPublicationAttempt(log)
    }

    def 'access key is injected into the simple pipeline'() {
        given:
        createSlaveAndTurnOnInjection()
        def mavenInstallationName = setupMavenInstallation()

        withInjectionConfig {
            accessKey = Secret.fromString("scans.gradle.com=secret")
        }
        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition(simplePipeline(mavenInstallationName), false))

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        j.assertLogContains("GRADLE_ENTERPRISE_ACCESS_KEY=scans.gradle.com=secret", build)
        j.assertLogNotContains("ERROR: Gradle Enterprise access key format is not valid", build)
        j.assertLogContains("[INFO] The Gradle Terms of Service have not been agreed to.", build)
    }

    def 'invalid access key is not injected into the simple pipeline'() {
        given:
        createSlaveAndTurnOnInjection()
        def mavenInstallationName = setupMavenInstallation()

        withInjectionConfig {
            accessKey = Secret.fromString("secret")
        }
        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition(simplePipeline(mavenInstallationName), false))

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        j.assertLogNotContains("GRADLE_ENTERPRISE_ACCESS_KEY=secret", build)
        j.assertLogContains("[INFO] The Gradle Terms of Service have not been agreed to.", build)

        and:
        StringUtils.countMatches(JenkinsRule.getLog(build), "ERROR: Gradle Enterprise access key format is not valid") == 1
    }

    def 'extension jars are copied and removed properly'() {
        when:
        def slave = createSlaveAndTurnOnInjection()
        def extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.exists()
        extensionDirectory.list().size() == 2
        extensionDirectory.list().find { it.name == GE_EXTENSION_JAR } != null
        extensionDirectory.list().find { it.name == CCUD_EXTENSION_JAR } == null
        extensionDirectory.list().find { it.name == CONFIGURATION_EXTENSION_JAR } != null

        noMavenOpts(slave)

        when:
        turnOffBuildInjectionAndRestart(slave)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 0

        noMavenOpts(slave)

        when:
        turnOnBuildInjectionAndRestart(slave)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 3
        extensionDirectory.list().find { it.name == GE_EXTENSION_JAR } != null
        extensionDirectory.list().find { it.name == CCUD_EXTENSION_JAR } != null
        extensionDirectory.list().find { it.name == CONFIGURATION_EXTENSION_JAR } != null

        noMavenOpts(slave)

        when:
        turnOnBuildInjectionAndRestart(slave, false)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 2
        extensionDirectory.list().find { it.name == GE_EXTENSION_JAR } != null
        extensionDirectory.list().find { it.name == CCUD_EXTENSION_JAR } == null
        extensionDirectory.list().find { it.name == CONFIGURATION_EXTENSION_JAR } != null

        noMavenOpts(slave)

        when:
        turnOnBuildInjectionAndRestart(slave)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 3
        extensionDirectory.list().find { it.name == GE_EXTENSION_JAR } != null
        extensionDirectory.list().find { it.name == CCUD_EXTENSION_JAR } != null
        extensionDirectory.list().find { it.name == CONFIGURATION_EXTENSION_JAR } != null

        noMavenOpts(slave)

        when:
        turnOffBuildInjectionAndRestart(slave)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 0

        noMavenOpts(slave)
    }

    def 'injection is enabled and disabled based on node labels'() {
        given:
        DumbSlave slave = createSlaveAndTurnOnInjection()
        FilePath extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        expect:
        extensionDirectory.exists()
        extensionDirectory.list().size() == 2

        when:
        withInjectionConfig {
            mavenInjectionDisabledNodes = labels('bar', 'foo')
        }
        restartSlave(slave)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 0

        when:
        withInjectionConfig {
            mavenInjectionDisabledNodes = null
            mavenInjectionEnabledNodes = labels('daz', 'foo')
        }
        restartSlave(slave)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.exists()
        extensionDirectory.list().size() == 2

        when:
        withInjectionConfig {
            mavenInjectionDisabledNodes = null
            mavenInjectionEnabledNodes = labels('daz')
        }
        restartSlave(slave)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 0
    }

    def 'build scan is published without GE plugin with Maven plugin'() {
        given:
        createSlaveAndTurnOnInjection()
        def pipelineJob = j.createProject(WorkflowJob)
        String mavenInstallationName = setupMavenInstallation()

        pipelineJob.setDefinition(new CpsFlowDefinition("""
node {
   stage('Build') {
        node('foo') {
            withMaven(maven: '$mavenInstallationName') {
                writeFile file: 'pom.xml', text: '$POM_XML'
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
        hasJarInMavenExt(log, GE_EXTENSION_JAR)
        !hasJarInMavenExt(log, CCUD_EXTENSION_JAR)
        hasBuildScanPublicationAttempt(log)
    }

    def 'build scan is published with CCUD extension applied'() {
        given:
        withInjectionConfig {
            enabled = true
            server = "https://scans.gradle.com"
            injectMavenExtension = true
            injectCcudExtension = true
        }

        createSlave('foo')
        def mavenInstallationName = setupMavenInstallation()

        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition(simplePipeline(mavenInstallationName), false))

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        def log = JenkinsRule.getLog(build)
        hasJarInMavenExt(log, GE_EXTENSION_JAR)
        hasJarInMavenExt(log, CCUD_EXTENSION_JAR)
        hasBuildScanPublicationAttempt(log)
    }

    def 'build scan is not published when global MAVEN_OPTS is set'() {
        given:
        def slave = createSlaveAndTurnOnInjection()
        def mavenInstallationName = setupMavenInstallation()

        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition(simplePipeline(mavenInstallationName), false))
        withGlobalEnvVars {
            put('MAVEN_OPTS', '-Dfoo=bar')
        }
        restartSlave(slave)

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        def log = JenkinsRule.getLog(build)
        log =~ /MAVEN_OPTS=.*-Dfoo=bar.*/
        !hasJarInMavenExt(log, GE_EXTENSION_JAR)
        !hasBuildScanPublicationAttempt(log)
    }

    def 'none of the environment variables for maven plugin integration are set when no build runs'() {
        when:
        def slave = createSlaveAndTurnOnInjection()

        then:
        getEnvVarFromNodeProperties(slave, MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL) == null
        getEnvVarFromNodeProperties(slave, MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER) == null
        getEnvVarFromNodeProperties(slave, MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH) == null

        when:
        withInjectionConfig {
            allowUntrusted = true
            injectCcudExtension = true
        }
        restartSlave(slave)

        then:
        getEnvVarFromNodeProperties(slave, MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL) == null
        getEnvVarFromNodeProperties(slave, MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER) == null
        getEnvVarFromNodeProperties(slave, MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH) == null

        when:
        turnOffBuildInjectionAndRestart(slave)

        then:
        getEnvVarFromNodeProperties(slave, MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL) == null
        getEnvVarFromNodeProperties(slave, MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER) == null
        getEnvVarFromNodeProperties(slave, MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH) == null
    }

    @Unroll
    @SuppressWarnings("GStringExpressionWithinString")
    def 'vcs repository pattern injection for pipeline remote project - #pattern #mavenSetup'(String pattern, String mavenSetup) {
        given:
        withInjectionConfig {
            injectionVcsRepositoryPatterns = pattern
        }
        createSlaveAndTurnOnInjection()
        def mavenInstallationName = setupMavenInstallation()
        def replacedMavenSetup = mavenSetup.replaceAll("mavenInstallationName", mavenInstallationName)

        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition("""
   stage('Build') {
        node('foo') {
            $replacedMavenSetup
                git branch: 'main', url: 'https://github.com/c00ler/simple-maven-project'
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
""", false))

        when:
        // In this case, the job had to be executed twice for SCM to be populated (we do the same for Gradle, though
        // for a different reason). Manual test didn't exhibit this problem, so most probably something caused by a test setup
        j.buildAndAssertSuccess(pipelineJob)
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        if (pattern.contains("simple-")) {
            j.assertLogContains("[INFO] The Gradle Terms of Service have not been agreed to.", build)
        } else {
            j.assertLogNotContains("[INFO] The Gradle Terms of Service have not been agreed to.", build)
        }

        where:
        pattern                                     |  mavenSetup
        "not-found-pattern, simple-"                | "withEnv([\"PATH+MAVEN=\${tool 'mavenInstallationName'}/bin\"]) {"
        "not-found-pattern, simple-"                | "withMaven(maven: 'mavenInstallationName') {"
        "this-one-does-not-match, this-one-too"     | "withEnv([\"PATH+MAVEN=\${tool 'mavenInstallationName'}/bin\"]) {"
        "this-one-does-not-match, this-one-too"     | "withMaven(maven: 'mavenInstallationName') {"
    }

    def 'vcs repository pattern injection for freestyle remote project - #pattern'(String pattern) {
        given:
        def mavenInstallationName = setupMavenInstallation()
        withInjectionConfig {
            injectionVcsRepositoryPatterns = pattern
        }
        def slave = createSlaveAndTurnOnInjection()

        def p = j.createFreeStyleProject()
        p.buildWrappersList.add(new BuildScanBuildWrapper())
        p.setScm(new GitSCM(GitSCM.createRepoList("https://github.com/c00ler/simple-maven-project", null), Collections.singletonList(new BranchSpec("main")), null, null, Collections.<GitSCMExtension>emptyList()))
        p.buildersList.add(new Maven("package", mavenInstallationName))
        p.setAssignedNode(slave)

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        if (pattern == "simple-") {
            j.assertLogContains("Publishing a build scan to scans.gradle.com requires accepting the Gradle Terms of Service", build)
        } else {
            j.assertLogNotContains("Publishing a build scan to scans.gradle.com requires accepting the Gradle Terms of Service", build)
        }

        where:
        pattern << ["simple-", "this-one-does-not-match"]
    }

    private static String simplePipeline(String mavenInstallationName) {
        """
node {
   stage('Build') {
        node('foo') {
            withEnv(["PATH+MAVEN=\${tool '$mavenInstallationName'}/bin"]) {
                writeFile file: 'pom.xml', text: '$POM_XML'
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

    private DumbSlave createSlaveAndTurnOnInjection() {
        withInjectionConfig {
            enabled = true
            server = 'https://scans.gradle.com'
            injectMavenExtension = true
        }

        createSlave('foo')
    }

    private static boolean hasJarInMavenExt(String log, String jar) {
        (log =~ /MAVEN_OPTS=.*-Dmaven\.ext\.class\.path=.*${jar}/).find()
    }

    private static boolean noMavenOpts(DumbSlave slave) {
        getMavenOptsFromNodeProperties(slave) == null
    }

    private static String getMavenOptsFromNodeProperties(DumbSlave slave) {
        return getEnvVarFromNodeProperties(slave, "MAVEN_OPTS")
    }

    private static String getEnvVarFromNodeProperties(DumbSlave slave, String envVar) {
        def all = slave.getNodeProperties().getAll(EnvironmentVariablesNodeProperty.class)
        return all.empty ? null : all.last().getEnvVars().get(envVar)
    }

    private static boolean hasBuildScanPublicationAttempt(String log) {
        (log =~ /The build scan was not published due to a configuration problem/).find()
    }

    void turnOffBuildInjectionAndRestart(DumbSlave slave) {
        withInjectionConfig {
            enabled = true
            server = 'https://scans.gradle.com'
            injectMavenExtension = false
        }

        // sync changes
        restartSlave(slave)
    }

    void turnOnBuildInjectionAndRestart(DumbSlave slave, Boolean useCCUD = true) {
        withInjectionConfig {
            enabled = true
            server = 'https://scans.gradle.com'
            injectMavenExtension = true
            injectCcudExtension = useCCUD
        }

        // sync changes
        restartSlave(slave)
    }
}
