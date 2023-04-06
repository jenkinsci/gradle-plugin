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
        try {
            InjectionConfig config = InjectionConfig.get();

            if (isInjectionDisabledGlobally(config)) {
                return;
            }

            boolean shouldDisableInjection = run.getAction(GitScmListener.GradleInjectionDisabledAction.class) != null;
            if (shouldDisableInjection) {
                envs.put(JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_GRADLE_INJECTION_ENABLED, "false");
            }
        } catch (Exception e) {
            listener.error("Error occurred when building environment for Gradle build");
        }
    }

}
