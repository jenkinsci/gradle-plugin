package hudson.plugins.gradle.injection;

import com.google.common.annotations.VisibleForTesting;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;

import javax.annotation.Nonnull;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class BuildScanEnvironmentContributor extends EnvironmentContributor {

    private static final Logger LOGGER = Logger.getLogger(BuildScanEnvironmentContributor.class.getName());

    public static final String GRADLE_ENTERPRISE_ACCESS_KEY = "GRADLE_ENTERPRISE_ACCESS_KEY";

    private final Supplier<InjectionConfig> injectionConfigSupplier;

    public BuildScanEnvironmentContributor() {
        this(InjectionConfig.SUPPLIER);
    }

    @VisibleForTesting
    BuildScanEnvironmentContributor(Supplier<InjectionConfig> injectionConfigSupplier) {
        this.injectionConfigSupplier = injectionConfigSupplier;
    }

    @Override
    public void buildEnvironmentFor(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull TaskListener listener) {
        Secret secret = injectionConfigSupplier.get().getAccessKey();
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
