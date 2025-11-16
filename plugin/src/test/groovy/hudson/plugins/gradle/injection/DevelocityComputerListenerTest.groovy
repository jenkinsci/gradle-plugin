package hudson.plugins.gradle.injection

import hudson.EnvVars
import hudson.model.Computer
import hudson.model.Node
import hudson.model.TaskListener
import hudson.plugins.gradle.injection.npm.NpmAgentDownloadHandler
import hudson.plugins.gradle.injection.npm.NpmBuildScanInjection
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.util.concurrent.Callable

import static hudson.plugins.gradle.injection.InjectionUtil.JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK

class DevelocityComputerListenerTest extends Specification {

    def globalEnvVars = new EnvVars([GLOBAL: "true"])
    def computer = Mock(Computer)
    def gradleBuildScanInjection = Mock(GradleBuildScanInjection)
    def mavenBuildScanInjection = Mock(MavenBuildScanInjection)
    def mavenExtensionDownloadHandler = Mock(MavenExtensionDownloadHandler)
    def npmBuildScanInjection = Mock(NpmBuildScanInjection)
    def npmAgentDownloadHandler = Mock(NpmAgentDownloadHandler)
    def injectionConfig = Mock(InjectionConfig)

    @Subject
    def gradleEnterpriseComputerListener =
            new DevelocityComputerListener(
                    gradleBuildScanInjection,
                    mavenBuildScanInjection,
                    mavenExtensionDownloadHandler,
                    npmBuildScanInjection,
                    npmAgentDownloadHandler,
                    { injectionConfig }
            )

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder()

    @Unroll
    def "performs injection when computer gets online (isGlobalAutoInjectionCheckEnabled=#isGlobalAutoInjectionCheckEnabled, isGlobalInjectionEnabled=#isGlobalInjectionEnabled)"() {
        given:
        if (isGlobalAutoInjectionCheckEnabled) {
            globalEnvVars.put(JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK, "true")
        }
        computer.buildEnvironment(_ as TaskListener) >> globalEnvVars
        injectionConfig.disabled >> !isGlobalInjectionEnabled

        def node = Mock(Node)
        def computerEnvVars = new EnvVars([COMPUTER: "true"])
        computer.getNode() >> node
        computer.getEnvironment() >> computerEnvVars
        def root = tempFolder.newFolder()

        mavenExtensionDownloadHandler.getExtensionDigests({ root }, injectionConfig) >> [:]
        npmBuildScanInjection.ifInjectionEnabledGlobally(injectionConfig, _ as Callable<ArtifactDigest>) >> Optional.empty()

        when:
        gradleEnterpriseComputerListener.onOnline(computer, Mock(TaskListener))

        then:
        (isInjectionExpected ? 1 : 0) * gradleBuildScanInjection.inject(node, globalEnvVars, computerEnvVars)
        (isInjectionExpected ? 1 : 0) * mavenBuildScanInjection.inject(node, [:])
        (isInjectionExpected ? 1 : 0) * npmBuildScanInjection.inject(node, null, computerEnvVars)

        where:
        isGlobalAutoInjectionCheckEnabled | isGlobalInjectionEnabled || isInjectionExpected
        false                             | true                     || true
        false                             | false                    || true
        true                              | true                     || true
        true                              | false                    || false
    }

    def "catches all errors when performing injection when computer gets online"() {
        given:
        computer.buildEnvironment(_ as TaskListener) >> globalEnvVars
        computer.name >> "testComputer"

        def node = Mock(Node)
        def computerEnvVars = new EnvVars([COMPUTER: "true"])
        computer.getNode() >> node
        computer.getEnvironment() >> computerEnvVars
        def root = tempFolder.newFolder()
        mavenExtensionDownloadHandler.getExtensionDigests({ root }, injectionConfig) >> [:]
        npmBuildScanInjection.ifInjectionEnabledGlobally(injectionConfig, _ as Callable<ArtifactDigest>) >> Optional.empty()

        gradleBuildScanInjection.inject(node, globalEnvVars, computerEnvVars) >> { throw new ExpectedException() }

        when:
        gradleEnterpriseComputerListener.onOnline(computer, Mock(TaskListener))

        then:
        noExceptionThrown()

        and: "other injections are not performed"
        0 * mavenBuildScanInjection.inject(node, [:])
        0 * npmBuildScanInjection.inject(node, null, computerEnvVars)
    }
}
