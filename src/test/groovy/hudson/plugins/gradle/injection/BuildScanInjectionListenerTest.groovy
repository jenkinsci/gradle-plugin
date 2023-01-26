package hudson.plugins.gradle.injection

import hudson.EnvVars
import hudson.model.Computer
import hudson.model.Node
import hudson.model.TaskListener
import spock.lang.Specification
import spock.lang.Unroll

class BuildScanInjectionListenerTest extends Specification {

    def injector = Mock(BuildScanInjection)
    def globalEnvVars = new EnvVars([GLOBAL: "true"])
    def computer = Mock(Computer)
    def injectionConfig = Mock(InjectionConfig)

    def buildScanInjectionListener =
        new BuildScanInjectionListener([injector], { globalEnvVars }, { [computer] }, { injectionConfig })

    @Unroll
    def "performs injection when computer gets online (isGlobalAutoInjectionCheckEnabled=#isGlobalAutoInjectionCheckEnabled, isGlobalInjectionEnabled=#isGlobalInjectionEnabled)"() {
        given:
        if (isGlobalAutoInjectionCheckEnabled) {
            globalEnvVars.put(BuildScanInjectionListener.JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK, "true")
        }
        computer.buildEnvironment(_ as TaskListener) >> globalEnvVars
        injectionConfig.disabled >> !isGlobalInjectionEnabled

        def node = Mock(Node)
        def computerEnvVars = new EnvVars([COMPUTER: "ture"])
        computer.getNode() >> node
        computer.getEnvironment() >> computerEnvVars

        when:
        buildScanInjectionListener.onOnline(computer, Mock(TaskListener))

        then:
        (isInjectionExpected ? 1 : 0) * injector.inject(node, globalEnvVars, computerEnvVars)

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
        def computerEnvVars = new EnvVars([COMPUTER: "ture"])
        computer.getNode() >> node
        computer.getEnvironment() >> computerEnvVars

        injector.inject(node, globalEnvVars, computerEnvVars) >> { throw new ExpectedException() }

        when:
        buildScanInjectionListener.onOnline(computer, Mock(TaskListener))

        then:
        noExceptionThrown()
    }

    @Unroll
    def "performs injection when configuration changes (isGlobalAutoInjectionCheckEnabled=#isGlobalAutoInjectionCheckEnabled, isGlobalInjectionEnabled=#isGlobalInjectionEnabled, isComputerOffline=#isComputerOffline)"() {
        given:
        if (isGlobalAutoInjectionCheckEnabled) {
            globalEnvVars.put(BuildScanInjectionListener.JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK, "true")
        }
        injectionConfig.disabled >> !isGlobalInjectionEnabled
        computer.offline >> isComputerOffline

        def node = Mock(Node)
        def computerEnvVars = new EnvVars([COMPUTER: "ture"])
        computer.getNode() >> node
        computer.getEnvironment() >> computerEnvVars

        when:
        buildScanInjectionListener.onConfigurationChange()

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
        buildScanInjectionListener.onConfigurationChange()

        then:
        noExceptionThrown()
    }

    private static class ExpectedException extends RuntimeException {

        ExpectedException() {
            super("expected")
        }
    }
}
