package hudson.plugins.gradle.injection

import hudson.EnvVars
import hudson.FilePath
import hudson.plugins.git.BranchSpec
import hudson.plugins.git.GitSCM
import hudson.plugins.git.UserRemoteConfig
import hudson.plugins.git.browser.CGit
import hudson.plugins.gradle.BaseMavenIntegrationTest
import hudson.plugins.gradle.BuildScanAction
import hudson.plugins.timestamper.TimestamperBuildWrapper
import hudson.slaves.DumbSlave
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.tasks.Maven
import hudson.util.Secret
import org.apache.commons.lang3.StringUtils
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jvnet.hudson.test.CreateFileBuilder
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Issue
import spock.lang.Unroll

import static hudson.plugins.gradle.injection.MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER
import static hudson.plugins.gradle.injection.MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH
import static hudson.plugins.gradle.injection.MavenBuildScanInjection.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL
import static hudson.plugins.gradle.injection.MavenSnippets.simplePom

@Unroll
class BuildScanInjectionMavenIntegrationTest extends BaseMavenIntegrationTest {

    private static final String DEVELOCITY_EXTENSION_JAR = "develocity-maven-extension.jar"
    private static final String CCUD_EXTENSION_JAR = "common-custom-user-data-maven-extension.jar"
    private static final String CONFIGURATION_EXTENSION_JAR = "configuration-maven-extension.jar"
    private static final String TOU_MSG = "The Gradle Terms of Use have not been agreed to"

    private static final List<String> ALL_EXTENSIONS = [DEVELOCITY_EXTENSION_JAR, CCUD_EXTENSION_JAR, CONFIGURATION_EXTENSION_JAR]

    private static final String POM_XML = '<?xml version="1.0" encoding="UTF-8"?><project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"><modelVersion>4.0.0</modelVersion><groupId>com.example</groupId><artifactId>my-pom</artifactId><version>0.1-SNAPSHOT</version><packaging>pom</packaging><name>my-pom</name><description>my-pom</description></project>'
    private static final String INJECT_CCUD = '[DEBUG] Executing extension: CommonCustomUserDataGradleEnterpriseListener'

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

    def 'does not take into account MAVEN_OPTS set on the node level'() {
        given:
        def mavenOpts = '-Dfoo=bar'
        def agent = createSlave('test', new EnvVars([MAVEN_OPTS: mavenOpts]))

        when:
        turnOnBuildInjectionAndRestart(agent)

        then:
        with(getMavenOptsFromNodeProperties(agent).split(" ").iterator()) {
            with(it.next()) {
                it.startsWith('-Dmaven.ext.class.path=')
                it.contains('develocity-maven-extension.jar')
                it.contains('common-custom-user-data-maven-extension.jar')
            }
            with(it.next()) {
                it == '-Ddevelocity.scan.uploadInBackground=false'
            }
            with(it.next()) {
                it == '-Dgradle.scan.uploadInBackground=false'
            }
            with(it.next()) {
                it == '-Ddevelocity.url=https://scans.gradle.com'
            }
            with(it.next()) {
                it == '-Dgradle.enterprise.url=https://scans.gradle.com'
            }
            with(it.next()) {
                it == '-Ddevelocity.scan.captureFileFingerprints=true'
            }
            !it.hasNext()
        }

        when:
        turnOffBuildInjectionAndRestart(agent)

        then:
        noMavenOpts(agent)

        and:
        EnvVars.getRemote(agent.getChannel())['MAVEN_OPTS'] == mavenOpts
    }

    def 'appends new properties to MAVEN_OPTS when auto-injection is enabled'() {
        given:
        def mavenOpts = '-Dfoo=bar'
        def agent = createSlave('test')

        withNodeEnvVars(agent) {
            put('MAVEN_OPTS', mavenOpts)
        }

        when:
        turnOnBuildInjectionAndRestart(agent, true, true)

        then:
        with(getMavenOptsFromNodeProperties(agent).split(" ").iterator()) {
            with(it.next()) {
                it == mavenOpts
            }
            with(it.next()) {
                it.startsWith('-Dmaven.ext.class.path=')
                it.contains('develocity-maven-extension.jar')
                it.contains('common-custom-user-data-maven-extension.jar')
            }
            with(it.next()) {
                it == '-Ddevelocity.scan.uploadInBackground=false'
            }
            with(it.next()) {
                it == '-Dgradle.scan.uploadInBackground=false'
            }
            with(it.next()) {
                it == '-Ddevelocity.url=https://scans.gradle.com'
            }
            with(it.next()) {
                it == '-Dgradle.enterprise.url=https://scans.gradle.com'
            }
            with(it.next()) {
                it == '-Ddevelocity.scan.captureFileFingerprints=true'
            }
            with(it.next()) {
                it == '-Dgradle.scan.captureGoalInputFiles=true'
            }
            !it.hasNext()
        }

        when:
        turnOffBuildInjectionAndRestart(agent)

        then:
        getMavenOptsFromNodeProperties(agent) == mavenOpts
    }

    def 'does not create new EnvironmentVariablesNodeProperty when MAVEN_OPTS changes'() {
        when:
        def slave = createSlaveAndTurnOnInjection()

        then:
        slave.getNodeProperties().getAll(EnvironmentVariablesNodeProperty.class).size() == 1

        hasJarInMavenExt(slave, DEVELOCITY_EXTENSION_JAR)
        !hasJarInMavenExt(slave, CCUD_EXTENSION_JAR)

        when:
        turnOnBuildInjectionAndRestart(slave)

        then:
        slave.getNodeProperties().getAll(EnvironmentVariablesNodeProperty.class).size() == 1

        hasJarInMavenExt(slave, DEVELOCITY_EXTENSION_JAR)
        hasJarInMavenExt(slave, CCUD_EXTENSION_JAR)

        when:
        turnOffBuildInjectionAndRestart(slave)

        then:
        slave.getNodeProperties().getAll(EnvironmentVariablesNodeProperty.class).size() == 1

        noMavenOpts(slave)
    }

    def 'build scan is published without GE plugin with simple pipeline'() {
        given:
        createSlaveAndTurnOnInjection()
        def mavenInstallationName = setupMavenInstallation()

        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition(simplePipeline(mavenInstallationName), false))

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        def log = JenkinsRule.getLog(build)
        hasJarInMavenExt(log, DEVELOCITY_EXTENSION_JAR)
        !hasJarInMavenExt(log, CCUD_EXTENSION_JAR)
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
        j.assertLogContains("DEVELOCITY_ACCESS_KEY=scans.gradle.com=secret", build)
        j.assertLogNotContains(INVALID_ACCESS_KEY_FORMAT_ERROR, build)
        j.assertLogContains("[INFO] The Gradle Terms of Use have not been agreed to.", build)
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
        j.assertLogContains("[INFO] The Gradle Terms of Use have not been agreed to.", build)

        and:
        StringUtils.countMatches(JenkinsRule.getLog(build), INVALID_ACCESS_KEY_FORMAT_ERROR) == 1
    }

    def 'extension jars are copied and removed properly and MAVEN_OPTS is set'() {
        when:
        def slave = createSlaveAndTurnOnInjection()
        def extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.exists()
        extensionDirectory.list().size() == 2
        extensionDirectory.list().find { it.name == DEVELOCITY_EXTENSION_JAR } != null
        extensionDirectory.list().find { it.name == CONFIGURATION_EXTENSION_JAR } != null

        hasJarInMavenExt(slave, DEVELOCITY_EXTENSION_JAR)
        !hasJarInMavenExt(slave, CCUD_EXTENSION_JAR)
        !hasJarInMavenExt(slave, CONFIGURATION_EXTENSION_JAR)

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
        extensionDirectory.list().find { it.name == DEVELOCITY_EXTENSION_JAR } != null
        extensionDirectory.list().find { it.name == CCUD_EXTENSION_JAR } != null
        extensionDirectory.list().find { it.name == CONFIGURATION_EXTENSION_JAR } != null

        hasJarInMavenExt(slave, DEVELOCITY_EXTENSION_JAR)
        hasJarInMavenExt(slave, CCUD_EXTENSION_JAR)
        !hasJarInMavenExt(slave, CONFIGURATION_EXTENSION_JAR)

        when:
        turnOnBuildInjectionAndRestart(slave, false)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 2
        extensionDirectory.list().find { it.name == DEVELOCITY_EXTENSION_JAR } != null
        extensionDirectory.list().find { it.name == CONFIGURATION_EXTENSION_JAR } != null

        hasJarInMavenExt(slave, DEVELOCITY_EXTENSION_JAR)
        !hasJarInMavenExt(slave, CCUD_EXTENSION_JAR)
        !hasJarInMavenExt(slave, CONFIGURATION_EXTENSION_JAR)

        when:
        turnOnBuildInjectionAndRestart(slave)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 3
        extensionDirectory.list().find { it.name == DEVELOCITY_EXTENSION_JAR } != null
        extensionDirectory.list().find { it.name == CCUD_EXTENSION_JAR } != null
        extensionDirectory.list().find { it.name == CONFIGURATION_EXTENSION_JAR } != null

        hasJarInMavenExt(slave, DEVELOCITY_EXTENSION_JAR)
        hasJarInMavenExt(slave, CCUD_EXTENSION_JAR)
        !hasJarInMavenExt(slave, CONFIGURATION_EXTENSION_JAR)

        when:
        turnOffBuildInjectionAndRestart(slave)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 0

        noMavenOpts(slave)
    }

    def "does not capture build agent errors if checking for errors is disabled"() {
        given:
        def mavenInstallationName = setupMavenInstallation()

        DumbSlave agent = createSlave('test')

        def p = j.createFreeStyleProject()
        p.setAssignedNode(agent)

        p.buildersList.add(new CreateFileBuilder('pom.xml', simplePom()))
        p.buildersList.add(new Maven('-Dcom.gradle.scan.trigger-synthetic-error=true -Ddevelocity.scan.trigger-synthetic-error=true package', mavenInstallationName))
        p.getBuildWrappersList().add(new TimestamperBuildWrapper())

        when:
        def firstRun = j.buildAndAssertSuccess(p)

        then:
        firstRun.getAction(BuildScanAction) == null

        when:
        withInjectionConfig {
            enabled = true
            server = "https://scans.gradle.com"
            injectMavenExtension = true
            checkForBuildAgentErrors = false
        }
        def secondRun = buildAndAssertFailure(p)

        then:
        secondRun.getAction(BuildScanAction) == null
    }

    def "captures build agent errors if checking for errors is enabled"() {
        given:
        def mavenInstallationName = setupMavenInstallation()

        DumbSlave agent = createSlave('test')

        def p = j.createFreeStyleProject()
        p.setAssignedNode(agent)

        p.buildersList.add(new CreateFileBuilder('pom.xml', simplePom()))
        p.buildersList.add(new Maven('-Dcom.gradle.scan.trigger-synthetic-error=true -Ddevelocity.scan.trigger-synthetic-error=true package', mavenInstallationName))
        p.getBuildWrappersList().add(new TimestamperBuildWrapper())

        when:
        def firstRun = j.buildAndAssertSuccess(p)

        then:
        firstRun.getAction(BuildScanAction) == null

        when:
        withInjectionConfig {
            enabled = true
            server = "https://scans.gradle.com"
            injectMavenExtension = true
            checkForBuildAgentErrors = true
        }
        def secondRun = buildAndAssertFailure(p)

        then:
        with(secondRun.getAction(BuildScanAction)) {
            scanUrls.isEmpty()
            !hasGradleErrors
            hasMavenErrors
        }
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
        hasJarInMavenExt(log, DEVELOCITY_EXTENSION_JAR)
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
        hasJarInMavenExt(log, DEVELOCITY_EXTENSION_JAR)
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
        !hasJarInMavenExt(log, DEVELOCITY_EXTENSION_JAR)
        !hasBuildScanPublicationAttempt(log)
    }

    def 'set all environment variables for maven plugin integration'() {
        when:
        def slave = createSlaveAndTurnOnInjection()

        then:
        getEnvVarFromNodeProperties(slave, JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL) == 'https://scans.gradle.com'
        getEnvVarFromNodeProperties(slave, JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER) == null
        assertMavenConfigClasspathJars(slave, DEVELOCITY_EXTENSION_JAR, CONFIGURATION_EXTENSION_JAR)

        when:
        withInjectionConfig {
            allowUntrusted = true
        }
        restartSlave(slave)

        then:
        getEnvVarFromNodeProperties(slave, JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL) == 'https://scans.gradle.com'
        getEnvVarFromNodeProperties(slave, JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER) == "true"
        assertMavenConfigClasspathJars(slave, DEVELOCITY_EXTENSION_JAR, CONFIGURATION_EXTENSION_JAR)

        when:
        withInjectionConfig {
            allowUntrusted = false
            injectCcudExtension = true
        }
        restartSlave(slave)

        then:
        getEnvVarFromNodeProperties(slave, JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL) == 'https://scans.gradle.com'
        getEnvVarFromNodeProperties(slave, JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER) == null
        assertMavenConfigClasspathJars(slave, DEVELOCITY_EXTENSION_JAR, CCUD_EXTENSION_JAR, CONFIGURATION_EXTENSION_JAR)

        when:
        turnOffBuildInjectionAndRestart(slave)

        then:
        getEnvVarFromNodeProperties(slave, JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL) == null
        getEnvVarFromNodeProperties(slave, JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER) == null
        getEnvVarFromNodeProperties(slave, JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH) == null
    }

    @SuppressWarnings("GStringExpressionWithinString")
    def 'vcs repository pattern injection for pipeline remote project - #filter #mavenSetup #shouldApplyAutoInjection'(
        String filter,
        String mavenSetup,
        boolean shouldApplyAutoInjection
    ) {
        given:
        withInjectionConfig {
            vcsRepositoryFilter = filter
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
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        if (shouldApplyAutoInjection) {
            j.assertLogContains("[INFO] The Gradle Terms of Use have not been agreed to.", build)
        } else {
            j.assertLogNotContains("[INFO] The Gradle Terms of Use have not been agreed to.", build)
        }

        where:
        filter                                      | mavenSetup                                                         | shouldApplyAutoInjection
        "+:not-found-pattern\n+:simple-"            | "withEnv([\"PATH+MAVEN=\${tool 'mavenInstallationName'}/bin\"]) {" | true
        "+:not-found-pattern\n+:simple-"            | "withMaven(maven: 'mavenInstallationName') {"                      | true
        "+:this-one-does-not-match\n+:this-one-too" | "withEnv([\"PATH+MAVEN=\${tool 'mavenInstallationName'}/bin\"]) {" | false
        "+:this-one-does-not-match\n+:this-one-too" | "withMaven(maven: 'mavenInstallationName') {"                      | false
    }

    def 'vcs repository pattern injection for freestyle remote project - #filter #shouldApplyAutoInjection'(
        String filter,
        boolean shouldApplyAutoInjection
    ) {
        given:
        mavenInstallationRule.mavenVersion = '3.9.2'
        mavenInstallationRule.addInstallation()
        withInjectionConfig {
            vcsRepositoryFilter = filter
        }
        createSlaveAndTurnOnInjection()

        def p = j.createFreeStyleProject()
        p.buildersList.add(new Maven('package -B', mavenInstallationRule.mavenVersion))
        p.setScm(
            new GitSCM(
                [new UserRemoteConfig("https://github.com/c00ler/simple-maven-project", null, null, null)],
                [new BranchSpec("main")], new CGit("https://github.com/c00ler/simple-maven-project"),
                "git",
                []
            )
        )

        def slave = createSlave('foo')
        p.setAssignedNode(slave)

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        if (shouldApplyAutoInjection) {
            j.assertLogContains(TOU_MSG, build)
        } else {
            j.assertLogNotContains(TOU_MSG, build)
        }

        where:
        filter                                      | shouldApplyAutoInjection
        "+:not-found-pattern\n+:simple-"            | true
        "+:this-one-does-not-match\n+:this-one-too" | false
    }

    @SuppressWarnings("GStringExpressionWithinString")
    def 'extension already applied in pipeline project and build scan attempted to publish to project configured host - #mavenSetup #isUrlEnforced'(String mavenSetup, boolean isUrlEnforced) {
        given:
        withInjectionConfig {
            enforceUrl = isUrlEnforced
        }
        createSlaveAndTurnOnInjection()

        def mavenInstallationName = setupMavenInstallation()
        def replacedMavenSetup = mavenSetup.replaceAll("mavenInstallationName", mavenInstallationName)

        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition("""
   stage('Build') {
        node('foo') {
            $replacedMavenSetup
                git branch: 'ge-extension', url: 'https://github.com/c00ler/simple-maven-project'
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
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        if (isUrlEnforced) {
            j.assertLogContains("Publishing build scan...", build)
        } else {
            // Project has localhost:8080 configured
            j.assertLogContains("[WARNING] No build scan will be published: Gradle Enterprise features were not enabled due to an unexpected error while contacting Gradle Enterprise", build)
        }

        where:
        mavenSetup                                                         | isUrlEnforced
        "withEnv([\"PATH+MAVEN=\${tool 'mavenInstallationName'}/bin\"]) {" | false
        "withMaven(maven: 'mavenInstallationName') {"                      | false
        "withEnv([\"PATH+MAVEN=\${tool 'mavenInstallationName'}/bin\"]) {" | true
        "withMaven(maven: 'mavenInstallationName') {"                      | true
    }

    def 'custom extension already applied in pipeline project and build scan attempted to publish to project configured host'() {
        given:
        withInjectionConfig {
            mavenExtensionCustomCoordinates = customExtension
            ccudExtensionCustomCoordinates = customCcud
            injectCcudExtension = true
        }
        createSlaveAndTurnOnInjection()
        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition("""
   stage('Build') {
        node('foo') {
            withMaven(maven: '${setupMavenInstallation()}') {
                git branch: 'custom-extension', url: 'https://github.com/c00ler/simple-maven-project'
                if (isUnix()) {
                    sh "env"
                    sh "mvn package -B -X"
                } else {
                    bat "set"
                    bat "mvn package -B -X"
                }
            }
        }
   }
""", false))

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        if (shouldInjectDv) {
            j.assertLogContains(TOU_MSG, build)
        } else {
            j.assertLogNotContains(TOU_MSG, build)
        }
        if (shouldInjectCcud) {
            j.assertLogContains(INJECT_CCUD, build)
        } else {
            j.assertLogNotContains(INJECT_CCUD, build)
        }

        where:
        customExtension                                        | customCcud                                             | shouldInjectDv | shouldInjectCcud
        null                                                   | 'org.apache.maven.extensions:maven-enforcer-extension' | true           | false
        null                                                   | null                                                   | true           | true
        'foo:bar:1.0'                                          | 'foo:bar:2.0'                                          | true           | true
        'org.apache.maven.extensions:maven-enforcer-extension' | 'org.apache.maven.extensions:maven-enforcer-extension' | false          | false
        // This case needs a real custom DV extension so that the injected CCUD works
        //'org.apache.maven.extensions:maven-enforcer-extension' | null                                                   | false          | true
    }

    def 'extension already applied in freestyle project and build scan attempted to publish to project configured host - #isUrlEnforced'(boolean isUrlEnforced) {
        given:
        mavenInstallationRule.mavenVersion = '3.9.6'
        mavenInstallationRule.addInstallation()
        withInjectionConfig {
            enforceUrl = isUrlEnforced
        }
        createSlaveAndTurnOnInjection()

        def project = j.createFreeStyleProject()
        project.buildersList.add(new Maven('package', mavenInstallationRule.mavenVersion))
        project.setScm(
            new GitSCM(
                [new UserRemoteConfig("https://github.com/c00ler/simple-maven-project", null, null, null)],
                [new BranchSpec("ge-extension")], new CGit("https://github.com/c00ler/simple-maven-project"),
                "git",
                []
            )
        )

        def slave = createSlave('foo')
        project.setAssignedNode(slave)

        when:
        def build = j.buildAndAssertSuccess(project)

        then:
        if (isUrlEnforced) {
            j.assertLogContains("Publishing build scan...", build)
        } else {
            // Project has localhost:8080 configured
            j.assertLogContains("[WARNING] No build scan will be published: Gradle Enterprise features were not enabled due to an unexpected error while contacting Gradle Enterprise", build)
        }

        where:
        isUrlEnforced << [false, true]
    }

    def 'custom extension already applied in freestyle project and build scan attempted to publish to project configured host'() {
        given:
        mavenInstallationRule.mavenVersion = '3.9.6'
        mavenInstallationRule.addInstallation()
        withInjectionConfig {
            mavenExtensionCustomCoordinates = customExtension
            ccudExtensionCustomCoordinates = customCcud
            injectCcudExtension = true
        }
        createSlaveAndTurnOnInjection()

        def project = j.createFreeStyleProject()
        project.buildersList.add(new Maven('package -B -X', mavenInstallationRule.mavenVersion))
        project.setScm(
            new GitSCM(
                [new UserRemoteConfig("https://github.com/c00ler/simple-maven-project", null, null, null)],
                [new BranchSpec("custom-extension")], new CGit("https://github.com/c00ler/simple-maven-project"),
                "git",
                []
            )
        )

        def slave = createSlave('foo')
        project.setAssignedNode(slave)

        when:
        def build = j.buildAndAssertSuccess(project)

        then:
        if (shouldInjectDv) {
            j.assertLogContains(TOU_MSG, build)
        } else {
            j.assertLogNotContains(TOU_MSG, build)
        }
        if (shouldInjectCcud) {
            j.assertLogContains(INJECT_CCUD, build)
        } else {
            j.assertLogNotContains(INJECT_CCUD, build)
        }

        where:
        customExtension                                        | customCcud                                             | shouldInjectDv | shouldInjectCcud
        null                                                   | 'org.apache.maven.extensions:maven-enforcer-extension' | true           | false
        null                                                   | null                                                   | true           | true
        'foo:bar:1.0'                                          | 'foo:bar:2.0'                                          | true           | true
        'org.apache.maven.extensions:maven-enforcer-extension' | 'org.apache.maven.extensions:maven-enforcer-extension' | false          | false
        // This case needs a real custom DV extension so that the injected CCUD works
        //'org.apache.maven.extensions:maven-enforcer-extension' | null                                                   | false          | true
    }

    private static void assertMavenConfigClasspathJars(DumbSlave slave, String... jars) {
        def classpath = getEnvVarFromNodeProperties(slave, JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH)
        assert classpath != null

        def files = classpath.split(slave.toComputer().isUnix() ? ":" : ";")

        assert files.length == jars.length
        jars.each {
            assert files.find { it.endsWith(it) } != null
        }
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

    private String setupMavenInstallation(mavenVersion = "3.9.6") {
        mavenInstallationRule.mavenVersion = mavenVersion
        mavenInstallationRule.addInstallation()

        mavenVersion
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

    private static boolean hasJarInMavenExt(DumbSlave slave, String jar) {
        def mavenOpts = getMavenOptsFromNodeProperties(slave)
        return mavenOpts && mavenOpts ==~ /.*-Dmaven\.ext\.class\.path=.*${jar}.*/
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
            mavenCaptureGoalInputFiles = false
        }

        // sync changes
        restartSlave(slave)
    }

    void turnOnBuildInjectionAndRestart(DumbSlave slave, Boolean useCCUD = true, boolean captureGoalInputFiles = true) {
        withInjectionConfig {
            enabled = true
            server = 'https://scans.gradle.com'
            injectMavenExtension = true
            injectCcudExtension = useCCUD
            mavenCaptureGoalInputFiles = captureGoalInputFiles
        }

        // sync changes
        restartSlave(slave)
    }
}
