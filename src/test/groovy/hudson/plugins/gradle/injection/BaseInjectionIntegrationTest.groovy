package hudson.plugins.gradle.injection

import hudson.EnvVars
import hudson.model.Label
import hudson.plugins.gradle.AbstractIntegrationTest
import hudson.slaves.DumbSlave
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.slaves.NodeProperty

class BaseInjectionIntegrationTest extends AbstractIntegrationTest {

    void restartSlave(DumbSlave slave) {
        j.disconnectSlave(slave)
        j.waitOnline(slave)
    }

    DumbSlave createSlave(String label, EnvVars env = null) {
        return j.createOnlineSlave(Label.get(label), env)
    }

    void configureEnvironmentVariables(DumbSlave slave, @DelegatesTo(EnvVars) Closure closure = {}) {
        NodeProperty nodeProperty = new EnvironmentVariablesNodeProperty()
        EnvVars env = nodeProperty.getEnvVars()

        env.put('JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_INJECTION', 'true')

        closure.setDelegate(env)
        closure.run()

        j.jenkins.globalNodeProperties.clear()
        j.jenkins.globalNodeProperties.add(nodeProperty)

        // sync changes
        restartSlave(slave)
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

    EnvVars withAdditionalGlobalEnvVars(@DelegatesTo(EnvVars) Closure closure) {
        NodeProperty nodeProperty = new EnvironmentVariablesNodeProperty()
        EnvVars env = nodeProperty.getEnvVars()

        closure.setDelegate(env)
        closure.run()

        j.jenkins.globalNodeProperties.add(nodeProperty)
        env
    }
}
