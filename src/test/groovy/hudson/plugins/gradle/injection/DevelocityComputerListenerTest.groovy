package hudson.plugins.gradle.injection

import hudson.EnvVars
import hudson.model.Computer
import hudson.model.Node
import hudson.model.TaskListener
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static hudson.plugins.gradle.injection.InjectionUtil.JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK

class DevelocityComputerListenerTest extends Specification {

    def globalEnvVars = new EnvVars([GLOBAL: "true"])
    def computer = Mock(Computer)
    def injector = Mock(BuildScanInjection)
    def injectionConfig = Mock(InjectionConfig)

    @Subject
    def gradleEnterpriseComputerListener =
        new DevelocityComputerListener(new DevelocityInjector(injector), { injectionConfig })

    @Unroll
    def "performs injection when computer gets online (isGlobalAutoInjectionCheckEnabled=#isGlobalAutoInjectionCheckEnabled, isGlobalInjectionEnabled=#isGlobalInjectionEnabled)"() {
        given:
        if (isGlobalAutoInjectionCheckEnabled) {
            globalEnvVars.put(JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK, "true")
        }
        computer.buildEnvironment(_ as TaskListener) >> globalEnvVars
        injectionConfig.disabled >> !isGlobalInjectionEnabled

        def node = Mock(Node)
        def computerEnvVars = new EnvVars([COMPUTER: "ture"])
        computer.getNode() >> node
        computer.getEnvironment() >> computerEnvVars

        when:
        gradleEnterpriseComputerListener.onOnline(computer, Mock(TaskListener))

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
        gradleEnterpriseComputerListener.onOnline(computer, Mock(TaskListener))

        then:
        noExceptionThrown()
    }
}
