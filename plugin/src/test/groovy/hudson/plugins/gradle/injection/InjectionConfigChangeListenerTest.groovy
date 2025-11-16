package hudson.plugins.gradle.injection

import hudson.EnvVars
import hudson.XmlFile
import hudson.model.Computer
import hudson.model.Node
import hudson.plugins.gradle.injection.npm.NpmAgentDownloadHandler
import hudson.plugins.gradle.injection.npm.NpmBuildScanInjection
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.util.concurrent.Callable

import static hudson.plugins.gradle.injection.InjectionUtil.JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK

class InjectionConfigChangeListenerTest extends Specification {

    private static final XmlFile UNUSED_XML_FILE = null

    def globalEnvVars = new EnvVars([GLOBAL: "true"])
    def computer = Mock(Computer)
    def gradleBuildScanInjection = Mock(GradleBuildScanInjection)
    def mavenBuildScanInjection = Mock(MavenBuildScanInjection)
    def mavenExtensionDownloadHandler = Mock(MavenExtensionDownloadHandler)
    def npmBuildScanInjection = Mock(NpmBuildScanInjection)
    def npmAgentDownloadHandler = Mock(NpmAgentDownloadHandler)
    def injectionConfig = Mock(InjectionConfig)

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder()

    @Subject
    def injectionConfigChangeListener =
            new InjectionConfigChangeListener(
                    gradleBuildScanInjection,
                    mavenBuildScanInjection,
                    mavenExtensionDownloadHandler,
                    npmBuildScanInjection,
                    npmAgentDownloadHandler,
                    { globalEnvVars },
                    { [computer] }
            )

    @Unroll
    def "performs injection when configuration changes (isGlobalAutoInjectionCheckEnabled=#isGlobalAutoInjectionCheckEnabled, isGlobalInjectionEnabled=#isGlobalInjectionEnabled, isComputerOffline=#isComputerOffline)"() {
        given:
        if (isGlobalAutoInjectionCheckEnabled) {
            globalEnvVars.put(JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK, "true")
        }
        injectionConfig.disabled >> !isGlobalInjectionEnabled
        computer.offline >> isComputerOffline

        def node = Mock(Node)
        def computerEnvVars = new EnvVars([COMPUTER: "true"])
        computer.getNode() >> node
        computer.getEnvironment() >> computerEnvVars
        def root = tempFolder.newFolder()
        mavenExtensionDownloadHandler.ensureExtensionsDownloaded({ root }, injectionConfig) >> [:]
        npmBuildScanInjection.ifInjectionEnabledGlobally(injectionConfig, _ as Callable<ArtifactDigest>) >> Optional.empty()

        when:
        injectionConfigChangeListener.onChange(injectionConfig, UNUSED_XML_FILE)

        then:
        (isInjectionExpected ? 1 : 0) * gradleBuildScanInjection.inject(node, globalEnvVars, computerEnvVars)
        (isInjectionExpected ? 1 : 0) * mavenBuildScanInjection.inject(node, [:])
        (isInjectionExpected ? 1 : 0) * npmBuildScanInjection.inject(node, null, computerEnvVars)

        where:
        isGlobalAutoInjectionCheckEnabled | isGlobalInjectionEnabled | isComputerOffline || isInjectionExpected
        false                             | true                     | false             || true
        false                             | false                    | false             || true
        false                             | true                     | true              || false
        false                             | false                    | true              || false
        true                              | true                     | false             || true
        true                              | false                    | false             || false
        true                              | true                     | true              || false
        true                              | false                    | true              || false
    }

    def "catches all errors when performing injection when configuration changes"() {
        given:
        computer.offline >> false
        computer.name >> "testComputer"

        def node = Mock(Node)
        def computerEnvVars = new EnvVars([COMPUTER: "true"])
        computer.getNode() >> node
        computer.getEnvironment() >> computerEnvVars
        def root = tempFolder.newFolder()
        mavenExtensionDownloadHandler.ensureExtensionsDownloaded({ root }, injectionConfig) >> [:]
        npmBuildScanInjection.ifInjectionEnabledGlobally(injectionConfig, _ as Callable<ArtifactDigest>) >> Optional.empty()

        gradleBuildScanInjection.inject(node, globalEnvVars, computerEnvVars) >> { throw new ExpectedException() }

        when:
        injectionConfigChangeListener.onChange(injectionConfig, UNUSED_XML_FILE)

        then:
        noExceptionThrown()

        and: "other injections are not performed"
        0 * mavenBuildScanInjection.inject(node, [:])
        0 * npmBuildScanInjection.inject(node, null, computerEnvVars)
    }
}
