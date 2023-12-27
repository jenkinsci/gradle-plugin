package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.PasswordParameterValue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.gradle.DevelocityLogger;
import hudson.util.Secret;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static hudson.plugins.gradle.injection.GradleInjectionAware.JENKINSGRADLEPLUGIN_GRADLE_PLUGIN_REPOSITORY_PASSWORD;

@Extension
public class BuildScanEnvironmentContributor extends EnvironmentContributor {

    @Override
    public void buildEnvironmentFor(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull TaskListener listener) {
        Secret secretKey = InjectionConfig.get().getAccessKey();
        Secret secretPassword = InjectionConfig.get().getGradlePluginRepositoryPassword();
        if ((secretKey == null && secretPassword == null) || alreadyExecuted(run)) {
            return;
        }

        run.addAction(DevelocityParametersAction.of(new DevelocityLogger(listener), secretKey, secretPassword));
    }

    private static boolean alreadyExecuted(@Nonnull Run run) {
        return run.getAction(DevelocityParametersAction.class) != null;
    }

    public static class DevelocityParametersAction extends ParametersAction {

        private static final String GRADLE_ENTERPRISE_ACCESS_KEY = "GRADLE_ENTERPRISE_ACCESS_KEY";
        private static final String GRADLE_ENTERPRISE_REPO_PASSWORD = JENKINSGRADLEPLUGIN_GRADLE_PLUGIN_REPOSITORY_PASSWORD;

        private static final DevelocityParametersAction EMPTY = new DevelocityParametersAction();

        DevelocityParametersAction(List<ParameterValue> parameters, Collection<String> additionalSafeParameters) {
            super(parameters, additionalSafeParameters);
        }

        DevelocityParametersAction() {
            super(Collections.emptyList());
        }

        static DevelocityParametersAction empty() {
            return EMPTY;
        }

        static DevelocityParametersAction of(DevelocityLogger logger, @Nullable Secret accessKey, @Nullable Secret repoPassword) {
            List<ParameterValue> values = new ArrayList<>();
            if (accessKey != null) {
                if (!DevelocityAccessKeyValidator.getInstance().isValid(accessKey.getPlainText())) {
                    logger.error("Develocity access key format is not valid");
                } else {
                    values.add(new PasswordParameterValue(GRADLE_ENTERPRISE_ACCESS_KEY, accessKey.getPlainText()));
                }
            }
            if (repoPassword != null) {
                values.add(new PasswordParameterValue(GRADLE_ENTERPRISE_REPO_PASSWORD, repoPassword.getPlainText()));
            }
            if (values.isEmpty()) {
                return DevelocityParametersAction.empty();
            }
            return new DevelocityParametersAction(
                values,
                Stream.of(GRADLE_ENTERPRISE_ACCESS_KEY, GRADLE_ENTERPRISE_REPO_PASSWORD).collect(Collectors.toSet())
            );
        }
    }
}
