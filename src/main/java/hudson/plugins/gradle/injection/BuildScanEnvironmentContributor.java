package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;

import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class BuildScanEnvironmentContributor extends EnvironmentContributor {

    private static final Logger LOGGER = Logger.getLogger(BuildScanEnvironmentContributor.class.getName());

    public static final String GRADLE_ENTERPRISE_ACCESS_KEY = "GRADLE_ENTERPRISE_ACCESS_KEY";

    @Override
    public void buildEnvironmentFor(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull TaskListener listener) {
        Secret secret = InjectionConfig.get().getAccessKey();
        if (secret == null) {
            return;
        }

        String accessKey = secret.getPlainText();
        if (!GradleEnterpriseAccessKeyValidator.getInstance().isValid(accessKey)) {
            LOGGER.log(Level.WARNING, "Gradle Enterprise access key format is not valid");
            return;
        }

        envs.put(GRADLE_ENTERPRISE_ACCESS_KEY, accessKey);
    }
}
