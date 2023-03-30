package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;

import javax.annotation.Nonnull;

import static hudson.plugins.gradle.injection.MavenOptsHandler.MAVEN_OPTS;

@Extension
public class MavenInjectionEnvironmentContributor extends EnvironmentContributor implements MavenInjectionAware {

    @Override
    public void buildEnvironmentFor(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull TaskListener listener) {
        InjectionConfig config = InjectionConfig.get();

        if (isInjectionDisabledGlobally(config)) {
            return;
        }

        NodePreparedMavenEnvsAction preparedMavenProperties = run.getAction(NodePreparedMavenEnvsAction.class);
        if (preparedMavenProperties == null) {
            return;
        }

        envs.put(MAVEN_OPTS, preparedMavenProperties.preparedMavenOpts);

        // Configuration needed to support https://plugins.jenkins.io/maven-plugin/
        envs.put(JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH, preparedMavenProperties.preparedMavenPluginConfigExtClasspath);

        envs.put(JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_SERVER_URL, config.getServer());
        if (config.isAllowUntrusted()) {
            envs.put(JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_ALLOW_UNTRUSTED_SERVER, "true");
        }
    }

}
