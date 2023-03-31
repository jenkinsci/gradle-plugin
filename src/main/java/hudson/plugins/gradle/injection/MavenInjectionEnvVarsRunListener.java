package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.Extension;
import hudson.maven.AbstractMavenBuild;
import hudson.model.FreeStyleBuild;
import hudson.model.InvisibleAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.io.IOException;

import static hudson.plugins.gradle.injection.MavenInjectionAware.JENKINSGRADLEPLUGIN_MAVEN_OPTS_PREPARED;
import static hudson.plugins.gradle.injection.MavenInjectionAware.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH_PREPARED;

@Extension
public class MavenInjectionEnvVarsRunListener extends RunListener<Run<?, ?>> {

    @Override
    public void onStarted(Run<?, ?> run, TaskListener listener) {
        if (run instanceof FreeStyleBuild || run instanceof WorkflowRun || run instanceof AbstractMavenBuild) {
            EnvVars envVars = getEnvVars(run, listener);
            if (envVars == null) {
                return;
            }

            String mavenOpts = envVars.get(JENKINSGRADLEPLUGIN_MAVEN_OPTS_PREPARED);
            String mavenPluginConfigExtClasspath = envVars.get(JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH_PREPARED);
            if (mavenOpts != null && mavenPluginConfigExtClasspath != null) {
                MavenInjectionEnvVarsAction mavenInjectionEnvVarsAction = run.getAction(MavenInjectionEnvVarsAction.class);
                if (mavenInjectionEnvVarsAction == null) {
                    run.addAction(new MavenInjectionEnvVarsAction(mavenOpts, mavenPluginConfigExtClasspath));
                }
            }
        }
    }

    private static EnvVars getEnvVars(Run<?, ?> run, TaskListener listener) {
        try {
            return run.getEnvironment(listener);
        } catch (IOException | InterruptedException e) {
            listener.error("Error while getting EnvVars from Run execution");

            return null;
        }
    }

    /**
     * Action that holds Maven environment variables to be set in {@link MavenInjectionEnvironmentContributor}.
     */
    public static class MavenInjectionEnvVarsAction extends InvisibleAction {

        public final String mavenOpts;
        public final String mavenPluginConfigExtClasspath;

        public MavenInjectionEnvVarsAction(String mavenOpts, String mavenPluginConfigExtClasspath) {
            this.mavenOpts = mavenOpts;
            this.mavenPluginConfigExtClasspath = mavenPluginConfigExtClasspath;
        }
    }

}
