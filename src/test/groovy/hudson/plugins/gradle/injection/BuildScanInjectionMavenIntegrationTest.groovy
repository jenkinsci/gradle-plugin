package hudson.plugins.gradle.injection

import hudson.FilePath
import hudson.slaves.DumbSlave
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

    def pomFile = '<?xml version="1.0" encoding="UTF-8"?><project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"><modelVersion>4.0.0</modelVersion><groupId>com.example</groupId><artifactId>my-pom</artifactId><version>0.1-SNAPSHOT</version><packaging>pom</packaging><name>my-pom</name><description>my-pom</description></project>'

    def 'build scan is published without GE plugin with simple pipeline'() {
        given:
        createSlaveAndTurnOnInjection()
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

    def 'Extension jars are copied and removed properly'() {
        given:
        DumbSlave slave = createSlaveAndTurnOnInjection()
        FilePath extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)
        def geExtensionFileName = getExtensionVersion(MavenExtensionsHandler.MavenExtension.GRADLE_ENTERPRISE.name)
        def ccudExtensionFileName = getExtensionVersion(MavenExtensionsHandler.MavenExtension.CCUD.name)

        expect:
        extensionDirectory.exists()
        extensionDirectory.list().size() == 1
        extensionDirectory.list().get(0).getName() as String == "gradle-enterprise-maven-extension-${geExtensionFileName}.jar"

        when:
        turnOffBuildInjectionAndRestart(slave)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 0

        when:
        turnOnBuildInjectionAndRestart(slave)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        def expected = ["common-custom-user-data-maven-extension-${ccudExtensionFileName}.jar", "gradle-enterprise-maven-extension-${geExtensionFileName}.jar"] as List<String>
        extensionDirectory.list().size() == 2
        extensionDirectory.list().collect { it.name }.toSorted() == expected

        when:
        turnOnBuildInjectionAndRestart(slave, false)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 1
        extensionDirectory.list().get(0).getName() as String == "gradle-enterprise-maven-extension-${geExtensionFileName}.jar"

        when:
        turnOnBuildInjectionAndRestart(slave)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 2
        extensionDirectory.list().collect { it.name }.toSorted() == expected

        when:
        turnOffBuildInjectionAndRestart(slave)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 0
    }

    def 'injection is enabled and disabled based on node labels'() {
        given:
        DumbSlave slave = createSlaveAndTurnOnInjection()
        FilePath extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        expect:
        extensionDirectory.exists()
        extensionDirectory.list().size() == 1

        when:
        withAdditionalGlobalEnvVars { put(MavenBuildScanInjection.FEATURE_TOGGLE_DISABLED_NODES, 'bar,foo') }
        restartSlave(slave)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.list().size() == 0

        when:
        withAdditionalGlobalEnvVars {
            put(MavenBuildScanInjection.FEATURE_TOGGLE_DISABLED_NODES, '')
            put(MavenBuildScanInjection.FEATURE_TOGGLE_ENABLED_NODES, 'daz,foo')
        }
        restartSlave(slave)
        extensionDirectory = slave.toComputer().node.rootPath.child(MavenExtensionsHandler.LIB_DIR_PATH)

        then:
        extensionDirectory.exists()
        extensionDirectory.list().size() == 1

        when:
        withAdditionalGlobalEnvVars {
            put(MavenBuildScanInjection.FEATURE_TOGGLE_DISABLED_NODES, '')
            put(MavenBuildScanInjection.FEATURE_TOGGLE_ENABLED_NODES, 'daz')
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
        withGlobalEnvVars {
            put('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_INJECTION', 'true')
            put('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_EXTENSION_VERSION', '1.14.2')
            put('JENKINSGRADLEPLUGIN_CCUD_EXTENSION_VERSION', '1.10.1')
        }

        createSlave('foo')
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
        def slave = createSlaveAndTurnOnInjection()
        def pipelineJob = j.createProject(WorkflowJob)
        pipelineJob.setDefinition(new CpsFlowDefinition(simplePipeline(), false))
        withAdditionalGlobalEnvVars { put('MAVEN_OPTS', '-Dfoo=bar') }
        restartSlave(slave)

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

    private DumbSlave createSlaveAndTurnOnInjection() {
        withGlobalEnvVars {
            put('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_INJECTION', 'true')
            put('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_EXTENSION_VERSION', '1.14.2')
        }

        createSlave('foo')
    }

    private static boolean hasJarInMavenExt(String log, String jar) {
        String version = getExtensionVersion(jar)
        (log =~ /MAVEN_OPTS=.*-Dmaven\.ext\.class\.path=.*${jar}-${version}\.jar/).find()
    }

    private static String getExtensionVersion(String jar) {
        new File(getClass().getResource("/versions/${jar}-version.txt").toURI()).getText()
    }

    private static boolean hasBuildScanPublicationAttempt(String log) {
        (log =~ /The build scan was not published due to a configuration problem/).find()
    }

    void turnOffBuildInjectionAndRestart(DumbSlave slave) {
        configureEnvironmentVariables(slave) {
            remove('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_EXTENSION_VERSION')
        }
    }

    void turnOnBuildInjectionAndRestart(DumbSlave slave, Boolean useCCUD = true) {
        configureEnvironmentVariables(slave) {
            put('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_EXTENSION_VERSION', '1.14.2')

            if (useCCUD) {
                put('JENKINSGRADLEPLUGIN_CCUD_EXTENSION_VERSION', '1.14.2')
            }
        }
    }
}
