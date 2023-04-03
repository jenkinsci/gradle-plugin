package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;

import javax.annotation.Nonnull;

@Extension
public class GradleInjectionEnvironmentContributor extends EnvironmentContributor implements GradleInjectionAware {

    @Override
    public void buildEnvironmentFor(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull TaskListener listener) {
        InjectionConfig config = InjectionConfig.get();

        if (isInjectionDisabledGlobally(config)) {
            return;
        }

        if (!InjectionUtil.isVcsRepositoryAllowed(run, config)) {
            return;
        }

        envs.put(JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_GRADLE_INJECTION_ENABLED, "true");

        envs.put(JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL, config.getServer());
        envs.put(JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION, config.getGradlePluginVersion());

        if (config.isAllowUntrusted()) {
            envs.put(JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER, "true");
        }

        String pluginRepositoryUrl = config.getGradlePluginRepositoryUrl();
        if (pluginRepositoryUrl != null && InjectionUtil.isValid(InjectionConfig.checkUrl(pluginRepositoryUrl))) {
            envs.put(JENKINSGRADLEPLUGIN_GRADLE_PLUGIN_REPOSITORY_URL, pluginRepositoryUrl);
        }

        String ccudPluginVersion = config.getCcudPluginVersion();
        if (ccudPluginVersion != null && InjectionUtil.isValid(InjectionConfig.checkVersion(ccudPluginVersion))) {
            envs.put(JENKINSGRADLEPLUGIN_CCUD_PLUGIN_VERSION, ccudPluginVersion);
        }
    }

}
