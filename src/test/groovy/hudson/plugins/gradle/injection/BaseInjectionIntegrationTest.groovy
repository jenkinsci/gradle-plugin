package hudson.plugins.gradle.injection

import hudson.EnvVars
import hudson.model.Label
import hudson.plugins.gradle.AbstractIntegrationTest
import hudson.plugins.gradle.config.GlobalConfig
import hudson.slaves.DumbSlave
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.slaves.NodeProperty

class BaseInjectionIntegrationTest extends AbstractIntegrationTest {

    void restartSlave(DumbSlave slave) {
        j.disconnectSlave(slave)
        j.waitOnline(slave)
    }

    DumbSlave createSlave(String label) {
        return j.createOnlineSlave(Label.get(label))
    }

    EnvVars withGlobalEnvVars(@DelegatesTo(EnvVars) Closure closure) {
        NodeProperty nodeProperty = new EnvironmentVariablesNodeProperty()
        EnvVars env = nodeProperty.getEnvVars()

        closure.setDelegate(env)
        closure.run()

        j.jenkins.globalNodeProperties.clear()
        j.jenkins.globalNodeProperties.add(nodeProperty)
        env
    }

    GlobalConfig withInjectionConfig(@DelegatesTo(GlobalConfig) Closure closure) {
        def config = GlobalConfig.get()

        closure.setDelegate(config)
        closure.run()

        config.save()
        config
    }

    static List<NodeLabelItem> labels(String... labels) {
        return labels.collect { new NodeLabelItem(it) }
    }
}
