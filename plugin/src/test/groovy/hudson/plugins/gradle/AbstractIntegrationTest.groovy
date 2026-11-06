package hudson.plugins.gradle

import hudson.EnvVars
import hudson.model.Cause
import hudson.model.FreeStyleBuild
import hudson.model.FreeStyleProject
import hudson.model.Label
import hudson.model.Node
import hudson.model.ParametersAction
import hudson.model.ParametersDefinitionProperty
import hudson.model.Result
import hudson.model.TextParameterDefinition
import hudson.model.TextParameterValue
import hudson.model.queue.QueueTaskFuture
import hudson.plugins.gradle.enriched.EnrichedSummaryConfig
import hudson.plugins.gradle.injection.InjectionConfig
import hudson.plugins.gradle.injection.NodeLabelItem
import hudson.remoting.Channel
import hudson.slaves.DumbSlave
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.slaves.NodeProperty
import hudson.slaves.SlaveComputer
import org.jenkinsci.plugins.pipeline.maven.GlobalPipelineMavenConfig
import org.jenkinsci.plugins.pipeline.maven.dao.PipelineMavenPluginDao
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.junit.rules.TestRule
import org.jvnet.hudson.test.FlagRule
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Base class for all integration tests.
 */
abstract class AbstractIntegrationTest extends Specification {

    public static final String INVALID_ACCESS_KEY_FORMAT_ERROR = "[Develocity] - [ERROR] - Develocity access key format is not valid"

    public final JenkinsRule j = new JenkinsRule()
    public final TestRule noSpaceInTmpDirs = FlagRule.systemProperty("jenkins.test.noSpaceInTmpDirs", "true")

    void restartSlave(DumbSlave slave) {
        SlaveComputer computer = slave.computer
        CountDownLatch oldChannelClosed = new CountDownLatch(1)
        Channel oldChannel = computer.channel
        if (oldChannel != null) {
            oldChannel.addListener(new Channel.Listener() {
                @Override
                void onClosed(Channel channel, IOException cause) {
                    oldChannelClosed.countDown()
                }
            })
        }
        j.disconnectSlave(slave)
        if (oldChannel != null) {
            assert oldChannelClosed.await(10, TimeUnit.SECONDS)
        }
        connectSlave(computer)
    }

    private static void connectSlave(SlaveComputer computer) {
        try {
            computer.connect(false).get()
        } finally {
            System.out.print(computer.log)
        }
    }

    DumbSlave createSlave(String label, EnvVars env = null) {
        return j.createOnlineSlave(Label.get(label), env)
    }

    EnvVars withGlobalEnvVars(@DelegatesTo(EnvVars) Closure closure) {
        NodeProperty nodeProperty = new EnvironmentVariablesNodeProperty()
        EnvVars env = nodeProperty.getEnvVars()

        env.with(closure)
        j.jenkins.globalNodeProperties.clear()
        j.jenkins.globalNodeProperties.add(nodeProperty)

        env
    }

    EnvVars withNodeEnvVars(Node node, @DelegatesTo(EnvVars) Closure closure) {
        NodeProperty nodeProperty = new EnvironmentVariablesNodeProperty()
        EnvVars env = nodeProperty.getEnvVars()

        env.with(closure)
        node.nodeProperties.clear()
        node.nodeProperties.add(nodeProperty)

        env
    }

    InjectionConfig withInjectionConfig(@DelegatesTo(InjectionConfig) Closure closure) {
        def config = InjectionConfig.get()

        config.with(closure)
        config.save()

        config
    }

    EnrichedSummaryConfig withEnrichedSummaryConfig(@DelegatesTo(EnrichedSummaryConfig) Closure closure) {
        def config = EnrichedSummaryConfig.get()

        config.with(closure)
        config.save()

        config
    }

    FreeStyleBuild buildAndAssertFailure(FreeStyleProject job) {
        j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0))
    }

    WorkflowRun buildAndAssertFailure(WorkflowJob job) {
        j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0))
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
    def cleanup() throws IOException {
        PipelineMavenPluginDao dao = GlobalPipelineMavenConfig.get().getDao()
        if (dao instanceof Closeable) {
            ((Closeable) dao).close()
        }
    }

}
