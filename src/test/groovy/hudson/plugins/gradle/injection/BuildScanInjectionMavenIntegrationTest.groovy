package hudson.plugins.gradle.injection

import hudson.FilePath
import hudson.slaves.DumbSlave
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.tasks.Maven
import jenkins.model.Jenkins
import jenkins.mvn.DefaultGlobalSettingsProvider
import jenkins.mvn.DefaultSettingsProvider
import jenkins.mvn.GlobalMavenConfig
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.junit.Rule
import org.junit.rules.RuleChain
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.ToolInstallations

class BuildScanInjectionMavenIntegrationTest extends BaseInjectionIntegrationTest {

    @Rule
    public final RuleChain rules = RuleChain.outerRule(noSpaceInTmpDirs).around(j)

    private static final String GE_EXTENSION_JAR = "gradle-enterprise-maven-extension.jar"
    private static final String CCUD_EXTENSION_JAR = "common-custom-user-data-maven-extension.jar"

    private static final String POM_XML = '<?xml version="1.0" encoding="UTF-8"?><project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"><modelVersion>4.0.0</modelVersion><groupId>com.example</groupId><artifactId>my-pom</artifactId><version>0.1-SNAPSHOT</version><packaging>pom</packaging><name>my-pom</name><description>my-pom</description></project>'

    def "doesn't copy extensions if they were not changed"() {
        when:
        def slave = createSlaveAndTurnOnInjection()
        turnOnBuildInjectionAndRestart(slave)
        def extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 2

        def originalGeExtension = extensionDirectory.list().find { it.name == GE_EXTENSION_JAR }
        originalGeExtension != null
        def originalGeExtensionLastModified = originalGeExtension.lastModified()
        originalGeExtensionLastModified > 0
        def originalGeExtensionDigest = originalGeExtension.digest()
        originalGeExtensionDigest != null

        def originalCcudExtension = extensionDirectory.list().find { it.name == CCUD_EXTENSION_JAR }
        originalCcudExtension != null
        def originalCcudExtensionLastModified = originalCcudExtension.lastModified()
        originalCcudExtensionLastModified > 0
        def originalCcudExtensionDigest = originalCcudExtension?.digest()
        originalCcudExtensionDigest != null

        when:
        restartSlave(slave)

        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        def updatedGeExtension = extensionDirectory.list().find { it.name == GE_EXTENSION_JAR }
        updatedGeExtension != null
        updatedGeExtension.lastModified() == originalGeExtensionLastModified
        updatedGeExtension.digest() == originalGeExtensionDigest

        def updatedCcudExtension = extensionDirectory.list().find { it.name == CCUD_EXTENSION_JAR }
        updatedCcudExtension != null
        updatedCcudExtension.lastModified() == originalCcudExtensionLastModified
        updatedCcudExtension.digest() == originalCcudExtensionDigest
    }

    def 'copies a new version of the same extension if it was changed'() {
        when:
        def slave = createSlaveAndTurnOnInjection()
        turnOnBuildInjectionAndRestart(slave)
        def extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 2

        def originalGeExtension = extensionDirectory.list().find { it.name == GE_EXTENSION_JAR }
        originalGeExtension != null
        def originalGeExtensionLastModified = originalGeExtension.lastModified()
        originalGeExtensionLastModified > 0
        def originalGeExtensionDigest = originalGeExtension.digest()
        originalGeExtensionDigest != null

        def originalCcudExtension = extensionDirectory.list().find { it.name == CCUD_EXTENSION_JAR }
        originalCcudExtension != null
        def originalCcudExtensionLastModified = originalCcudExtension.lastModified()
        originalCcudExtensionLastModified > 0
        def originalCcudExtensionDigest = originalCcudExtension?.digest()
        originalCcudExtensionDigest != null

        when:
        def random = new Random()

        def geExtensionRandomBytes = new byte[10]
        random.nextBytes(geExtensionRandomBytes)

        originalGeExtension.copyFrom(new ByteArrayInputStream(geExtensionRandomBytes))

        def ccudExtensionRandomBytes = new byte[10]
        random.nextBytes(ccudExtensionRandomBytes)

        originalCcudExtension.copyFrom(new ByteArrayInputStream(ccudExtensionRandomBytes))

        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().find { it.name == GE_EXTENSION_JAR }?.lastModified() != originalGeExtensionLastModified
        extensionDirectory.list().find { it.name == CCUD_EXTENSION_JAR }?.lastModified() != originalCcudExtensionLastModified

        when:
        restartSlave(slave)

        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        def updatedGeExtension = extensionDirectory.list().find { it.name == GE_EXTENSION_JAR }
        updatedGeExtension != null
        updatedGeExtension.lastModified() != originalGeExtensionLastModified
        updatedGeExtension.digest() == originalGeExtensionDigest

        def updatedCcudExtension = extensionDirectory.list().find { it.name == CCUD_EXTENSION_JAR }
        updatedCcudExtension != null
        updatedCcudExtension.lastModified() != originalCcudExtensionLastModified
        updatedCcudExtension.digest() == originalCcudExtensionDigest
    }

    def 'does not create new EnvironmentVariablesNodeProperty when MAVEN_OPTS changes'() {
        when:
        def slave = createSlaveAndTurnOnInjection()

        then:
        slave.getNodeProperties().getAll(EnvironmentVariablesNodeProperty.class).size() == 1

        hasJarInMavenExt(slave, GE_EXTENSION_JAR)
        !hasJarInMavenExt(slave, CCUD_EXTENSION_JAR)

        when:
        turnOnBuildInjectionAndRestart(slave)

        then:
        slave.getNodeProperties().getAll(EnvironmentVariablesNodeProperty.class).size() == 1

        hasJarInMavenExt(slave, GE_EXTENSION_JAR)
        hasJarInMavenExt(slave, CCUD_EXTENSION_JAR)

        when:
        turnOffBuildInjectionAndRestart(slave)

        then:
        slave.getNodeProperties().getAll(EnvironmentVariablesNodeProperty.class).size() == 1

        getMavenOptsFromNodeProperties(slave) == ""
    }

    def 'build scan is published without GE plugin with simple pipeline'() {
        given:
        createSlaveAndTurnOnInjection()
        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition(simplePipeline(), false))

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        def log = JenkinsRule.getLog(build)
        hasJarInMavenExt(log, GE_EXTENSION_JAR)
        !hasJarInMavenExt(log, CCUD_EXTENSION_JAR)
        hasBuildScanPublicationAttempt(log)
    }

    def 'extension jars are copied and removed properly and MAVEN_OPTS is set'() {
        when:
        def slave = createSlaveAndTurnOnInjection()
        def extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.exists()
        extensionDirectory.list().size() == 1
        extensionDirectory.list().find { it.name == GE_EXTENSION_JAR } != null

        hasJarInMavenExt(slave, GE_EXTENSION_JAR)
        !hasJarInMavenExt(slave, CCUD_EXTENSION_JAR)

        when:
        turnOffBuildInjectionAndRestart(slave)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 0

        getMavenOptsFromNodeProperties(slave) == ""

        when:
        turnOnBuildInjectionAndRestart(slave)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 2
        extensionDirectory.list().find { it.name == GE_EXTENSION_JAR } != null
        extensionDirectory.list().find { it.name == CCUD_EXTENSION_JAR } != null

        hasJarInMavenExt(slave, GE_EXTENSION_JAR)
        hasJarInMavenExt(slave, CCUD_EXTENSION_JAR)

        when:
        turnOnBuildInjectionAndRestart(slave, false)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 1
        extensionDirectory.list().find { it.name == GE_EXTENSION_JAR } != null

        hasJarInMavenExt(slave, GE_EXTENSION_JAR)
        !hasJarInMavenExt(slave, CCUD_EXTENSION_JAR)

        when:
        turnOnBuildInjectionAndRestart(slave)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 2
        extensionDirectory.list().find { it.name == GE_EXTENSION_JAR } != null
        extensionDirectory.list().find { it.name == CCUD_EXTENSION_JAR } != null

        hasJarInMavenExt(slave, GE_EXTENSION_JAR)
        hasJarInMavenExt(slave, CCUD_EXTENSION_JAR)

        when:
        turnOffBuildInjectionAndRestart(slave)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 0

        getMavenOptsFromNodeProperties(slave) == ""
    }

    def 'injection is enabled and disabled based on node labels'() {
        given:
        DumbSlave slave = createSlaveAndTurnOnInjection()
        FilePath extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        expect:
        extensionDirectory.exists()
        extensionDirectory.list().size() == 1

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
        extensionDirectory.list().size() == 1

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
            mavenExtensionVersion = '1.14.2'
            ccudExtensionVersion = '1.10.1'
        }

        createSlave('foo')
        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition(simplePipeline(), false))

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
        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition(simplePipeline(), false))
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

    private static String simplePipeline() {
        """
node {
   stage('Build') {
        node('foo') {
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
            mavenExtensionVersion = '1.14.2'
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

    private static String getMavenOptsFromNodeProperties(DumbSlave slave) {
        def all = slave.getNodeProperties().getAll(EnvironmentVariablesNodeProperty.class)
        return all?.last()?.getEnvVars()?.get("MAVEN_OPTS")
    }

    private static boolean hasBuildScanPublicationAttempt(String log) {
        (log =~ /The build scan was not published due to a configuration problem/).find()
    }

    void turnOffBuildInjectionAndRestart(DumbSlave slave) {
        withInjectionConfig {
            enabled = true
            server = 'https://scans.gradle.com'
            mavenExtensionVersion = null
        }

        // sync changes
        restartSlave(slave)
    }

    void turnOnBuildInjectionAndRestart(DumbSlave slave, Boolean useCCUD = true) {
        withInjectionConfig {
            enabled = true
            server = 'https://scans.gradle.com'
            mavenExtensionVersion = '1.14.2'
            ccudExtensionVersion = useCCUD ? '1.11.1' : null
        }

        // sync changes
        restartSlave(slave)
    }
}
