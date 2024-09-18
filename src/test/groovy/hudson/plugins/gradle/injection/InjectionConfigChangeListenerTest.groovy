package hudson.plugins.gradle.injection

import hudson.EnvVars
import hudson.XmlFile
import hudson.model.Computer
import hudson.model.Node
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static hudson.plugins.gradle.injection.InjectionUtil.JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK

@Ignore
class InjectionConfigChangeListenerTest extends Specification {

    private static final XmlFile UNUSED_XML_FILE = null

    def globalEnvVars = new EnvVars([GLOBAL: "true"])
    def computer = Mock(Computer)
    def gradleBuildScanInjection = Mock(GradleBuildScanInjection)
    def mavenBuildScanInjection = Mock(MavenBuildScanInjection)
    def mavenExtensionDownloadHandler = Mock(MavenExtensionDownloadHandler)
    def injectionConfig = Mock(InjectionConfig)

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder()

    @Subject
    def injectionConfigChangeListener =
            new InjectionConfigChangeListener(gradleBuildScanInjection, mavenBuildScanInjection, mavenExtensionDownloadHandler, { globalEnvVars }, { [computer] })

    @Unroll
    def "performs injection when configuration changes (isGlobalAutoInjectionCheckEnabled=#isGlobalAutoInjectionCheckEnabled, isGlobalInjectionEnabled=#isGlobalInjectionEnabled, isComputerOffline=#isComputerOffline)"() {
        given:
        if (isGlobalAutoInjectionCheckEnabled) {
            globalEnvVars.put(JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK, "true")
        }
        injectionConfig.disabled >> !isGlobalInjectionEnabled
        computer.offline >> isComputerOffline

        def node = Mock(Node)
        def computerEnvVars = new EnvVars([COMPUTER: "ture"])
        computer.getNode() >> node
        computer.getEnvironment() >> computerEnvVars
        def root = tempFolder.newFolder()
        mavenExtensionDownloadHandler.ensureExtensionsDownloaded({ root }, injectionConfig) >> {}

        when:
        injectionConfigChangeListener.onChange(injectionConfig, UNUSED_XML_FILE)

        then:
        (isInjectionExpected ? 1 : 0) * gradleBuildScanInjection.inject(node, globalEnvVars, computerEnvVars)

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
        def computerEnvVars = new EnvVars([COMPUTER: "ture"])
        computer.getNode() >> node
        computer.getEnvironment() >> computerEnvVars
        def root = tempFolder.newFolder()
        mavenExtensionDownloadHandler.ensureExtensionsDownloaded({ root }, injectionConfig) >> {}

        gradleBuildScanInjection.inject(node, globalEnvVars, computerEnvVars) >> { throw new ExpectedException() }
        mavenBuildScanInjection.inject(node, [:])

        when:
        injectionConfigChangeListener.onChange(injectionConfig, UNUSED_XML_FILE)

        then:
        noExceptionThrown()
    }
}
