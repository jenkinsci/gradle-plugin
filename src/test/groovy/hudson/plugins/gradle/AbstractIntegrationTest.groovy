package hudson.plugins.gradle

import hudson.EnvVars
import hudson.model.Cause
import hudson.model.FreeStyleBuild
import hudson.model.FreeStyleProject
import hudson.model.Label
import hudson.model.ParametersAction
import hudson.model.ParametersDefinitionProperty
import hudson.model.TextParameterDefinition
import hudson.model.TextParameterValue
import hudson.model.queue.QueueTaskFuture
import hudson.plugins.gradle.injection.InjectionConfig
import hudson.plugins.gradle.injection.NodeLabelItem
import hudson.slaves.DumbSlave
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.slaves.NodeProperty
import org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao
import org.junit.rules.TestRule
import org.jvnet.hudson.test.FlagRule
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Specification

/**
 * Base class for all integration tests.
 */
class AbstractIntegrationTest extends Specification {

    public final JenkinsRule j = new JenkinsRule()
    public final TestRule noSpaceInTmpDirs = FlagRule.systemProperty("jenkins.test.noSpaceInTmpDirs", "true")

    protected AbstractIntegrationTest() {
    }

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

    InjectionConfig withInjectionConfig(@DelegatesTo(InjectionConfig) Closure closure) {
        def config = InjectionConfig.get()

        closure.setDelegate(config)
        closure.run()

        config.save()
        config
    }

    static List<NodeLabelItem> labels(String... labels) {
        return labels.collect { new NodeLabelItem(it) }
    }

    static QueueTaskFuture<FreeStyleBuild> triggerBuildWithParameter(FreeStyleProject p, String parameterName, String value) {
        p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new TextParameterValue(parameterName, value)))
    }

    static addParameter(FreeStyleProject p, String parameterName) {
        p.addProperty(new ParametersDefinitionProperty(new TextParameterDefinition(parameterName, null, null)))
    }

    // avoid DB locking between tests
    // https://github.com/jenkinsci/pipeline-maven-plugin/blob/d1f54279b2cd55266e8a9dd0eb2e600fe1d64f76/jenkins-plugin/src/test/java/org/jenkinsci/plugins/pipeline/maven/AbstractIntegrationTest.java#L53
    void cleanup() throws IOException {
        PipelineMavenPluginDao dao = GlobalPipelineMavenConfig.get().getDao()
        if (dao instanceof Closeable) {
            ((Closeable) dao).close()
        }
    }

}
