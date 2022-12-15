package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.gradle.config.GlobalConfig;
import hudson.util.Secret;

import javax.annotation.Nonnull;

@Extension
public class BuildScanEnvironmentContributor extends EnvironmentContributor {

    private static final String GRADLE_ENTERPRISE_ACCESS_KEY = "GRADLE_ENTERPRISE_ACCESS_KEY";

    @Override
    public void buildEnvironmentFor(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull TaskListener listener) {
        Secret accessKey = GlobalConfig.get().getAccessKey();
        if (accessKey != null) {
            envs.put(GRADLE_ENTERPRISE_ACCESS_KEY, accessKey.getPlainText());
        }
    }
}
