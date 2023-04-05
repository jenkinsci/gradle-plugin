package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.gradle.injection.GitScmListener.MavenInjectionDisabledMavenOptsAction;
import hudson.plugins.gradle.injection.MavenInjectionRunListener.MavenInjectionEnvVarsAction;

import javax.annotation.Nonnull;

import static hudson.plugins.gradle.injection.MavenOptsHandler.MAVEN_OPTS;

@Extension
public class MavenInjectionEnvironmentContributor extends EnvironmentContributor implements MavenInjectionAware {

    @Override
    public void buildEnvironmentFor(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull TaskListener listener) {
        try {
            InjectionConfig config = InjectionConfig.get();

            if (isInjectionDisabledGlobally(config)) {
                return;
            }

            MavenInjectionDisabledMavenOptsAction mavenInjectionDisabledMavenOptsAction = run.getAction(MavenInjectionDisabledMavenOptsAction.class);
            if (mavenInjectionDisabledMavenOptsAction != null) {
                envs.put(MAVEN_OPTS, mavenInjectionDisabledMavenOptsAction.mavenOpts);

                // Configuration needed to support https://plugins.jenkins.io/maven-plugin/
                envs.remove(JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH);
            } else {
                MavenInjectionEnvVarsAction mavenInjectionEnvVarsAction = run.getAction(MavenInjectionEnvVarsAction.class);
                if (mavenInjectionEnvVarsAction == null) {
                    return;
                }

                envs.put(MAVEN_OPTS, mavenInjectionEnvVarsAction.mavenOpts);

                // Configuration needed to support https://plugins.jenkins.io/maven-plugin/
                envs.put(JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH, mavenInjectionEnvVarsAction.mavenPluginConfigExtClasspath);

                envs.put(JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL, config.getServer());
                if (config.isAllowUntrusted()) {
                    envs.put(JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER, "true");
                }
            }
        } catch (Exception e) {
            listener.error("Error occurred when building environment for Maven build");
        }
    }

}
