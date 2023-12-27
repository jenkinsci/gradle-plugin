package hudson.plugins.gradle.injection

import hudson.EnvVars
import hudson.XmlFile
import hudson.model.Computer
import hudson.model.Node
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static hudson.plugins.gradle.injection.InjectionUtil.JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK

class InjectionConfigChangeListenerTest extends Specification {

    private static final XmlFile UNUSED_XML_FILE = null

    def globalEnvVars = new EnvVars([GLOBAL: "true"])
    def computer = Mock(Computer)
    def injector = Mock(BuildScanInjection)
    def injectionConfig = Mock(InjectionConfig)

    @Subject
    def injectionConfigChangeListener =
        new InjectionConfigChangeListener(new DevelocityInjector(injector), { globalEnvVars }, { [computer] })

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

        when:
        injectionConfigChangeListener.onChange(injectionConfig, UNUSED_XML_FILE)

        then:
        (isInjectionExpected ? 1 : 0) * injector.inject(node, globalEnvVars, computerEnvVars)

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

        injector.inject(node, globalEnvVars, computerEnvVars) >> { throw new ExpectedException() }

        when:
        injectionConfigChangeListener.onChange(injectionConfig, UNUSED_XML_FILE)

        then:
        noExceptionThrown()
    }
}
