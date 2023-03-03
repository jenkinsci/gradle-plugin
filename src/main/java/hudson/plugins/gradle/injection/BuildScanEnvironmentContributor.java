package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.InvisibleAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;

import javax.annotation.Nonnull;

@Extension
public class BuildScanEnvironmentContributor extends EnvironmentContributor {

    public static final String GRADLE_ENTERPRISE_ACCESS_KEY = "GRADLE_ENTERPRISE_ACCESS_KEY";

    @Override
    public void buildEnvironmentFor(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull TaskListener listener) {
        Secret secret = InjectionConfig.get().getAccessKey();
        if (secret == null) {
            return;
        }

        String accessKey = secret.getPlainText();
        if (!GradleEnterpriseAccessKeyValidator.getInstance().isValid(accessKey)) {
            boolean shouldLog = run.getAction(InvalidGradleEnterpriseAccessKey.class) == null;
            if (shouldLog) {
                listener.error("Gradle Enterprise access key format is not valid");
                run.addAction(InvalidGradleEnterpriseAccessKey.INSTANCE);
            }
            return;
        }

        envs.put(GRADLE_ENTERPRISE_ACCESS_KEY, accessKey);
    }

    /**
     * Marker action to ensure that we log error only once.
     */
    public static class InvalidGradleEnterpriseAccessKey extends InvisibleAction {

        public static final InvalidGradleEnterpriseAccessKey INSTANCE = new InvalidGradleEnterpriseAccessKey();

        private InvalidGradleEnterpriseAccessKey() {
        }
    }
}
