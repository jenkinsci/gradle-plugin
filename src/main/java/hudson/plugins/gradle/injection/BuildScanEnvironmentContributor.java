package hudson.plugins.gradle.injection;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.PasswordParameterValue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.gradle.DevelocityLogger;
import hudson.plugins.gradle.injection.token.ShortLivedTokenClient;
import hudson.util.Secret;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Extension
public class BuildScanEnvironmentContributor extends EnvironmentContributor {

    private final ShortLivedTokenClient tokenClient;

    public BuildScanEnvironmentContributor() {
        this.tokenClient = new ShortLivedTokenClient();
    }

    public BuildScanEnvironmentContributor(ShortLivedTokenClient tokenClient) {
        this.tokenClient = tokenClient;
    }

    @Override
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public void buildEnvironmentFor(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull TaskListener listener) {
        Secret secretKey = InjectionConfig.get().getAccessKey();
        Secret secretPassword = InjectionConfig.get().getGradlePluginRepositoryPassword();
        if ((secretKey == null && secretPassword == null) || alreadyExecuted(run)) {
            return;
        }

        Secret shortLivedToken = null;
        DevelocityLogger logger = new DevelocityLogger(listener);
        if (secretKey != null && DevelocityAccessKey.isValid(secretKey.getPlainText())) {
            shortLivedToken = tokenClient.get(InjectionConfig.get().getServer(),
                    DevelocityAccessKey.parse(secretKey.getPlainText()),
                    InjectionConfig.get().getShortLivedTokenExpiry())
                .map(k -> Secret.fromString(k.getRawAccessKey()))
                .orElse(null);
        } else {
            logger.error("Develocity access key format is not valid");
        }

        run.addAction(DevelocityParametersAction.of(logger, shortLivedToken, secretPassword));
    }

    private static boolean alreadyExecuted(@Nonnull Run run) {
        return run.getAction(DevelocityParametersAction.class) != null;
    }

    public static class DevelocityParametersAction extends ParametersAction {

        private static final String GRADLE_ENTERPRISE_ACCESS_KEY = "GRADLE_ENTERPRISE_ACCESS_KEY";
        private static final String DEVELOCITY_ACCESS_KEY = "DEVELOCITY_ACCESS_KEY";
        private static final String GRADLE_PLUGIN_REPOSITORY_PASSWORD = InitScriptVariables.GRADLE_PLUGIN_REPOSITORY_PASSWORD.getEnvVar();

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

        private static DevelocityParametersAction of(DevelocityLogger logger, @Nullable Secret shortLivedToken, @Nullable Secret repoPassword) {
            List<ParameterValue> values = new ArrayList<>();
            if (shortLivedToken != null) {
                values.add(new PasswordParameterValue(GRADLE_ENTERPRISE_ACCESS_KEY, shortLivedToken.getPlainText()));
                values.add(new PasswordParameterValue(DEVELOCITY_ACCESS_KEY, shortLivedToken.getPlainText()));
            }
            if (repoPassword != null) {
                values.add(new PasswordParameterValue(GRADLE_PLUGIN_REPOSITORY_PASSWORD, repoPassword.getPlainText()));
            }
            if (values.isEmpty()) {
                return DevelocityParametersAction.empty();
            }
            return new DevelocityParametersAction(
                values,
                Stream.of(GRADLE_ENTERPRISE_ACCESS_KEY, DEVELOCITY_ACCESS_KEY, GRADLE_PLUGIN_REPOSITORY_PASSWORD).collect(Collectors.toSet())
            );
        }
    }
}
