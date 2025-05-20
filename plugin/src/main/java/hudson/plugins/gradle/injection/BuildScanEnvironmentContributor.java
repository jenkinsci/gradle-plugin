package hudson.plugins.gradle.injection;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
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
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Extension
public class BuildScanEnvironmentContributor extends EnvironmentContributor {

    private final ShortLivedTokenClient tokenClient;

    public BuildScanEnvironmentContributor() {
        this.tokenClient = new ShortLivedTokenClient(InjectionConfig.get().isAllowUntrusted());
    }

    public BuildScanEnvironmentContributor(ShortLivedTokenClient tokenClient) {
        this.tokenClient = tokenClient;
    }

    @Override
    public void buildEnvironmentFor(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull TaskListener listener) {
        if (alreadyExecuted(run)) {
            return;
        }

        String accessKeyCredentialId = InjectionConfig.get().getAccessKeyCredentialId();
        String gradlePluginRepositoryCredentialId = InjectionConfig.get().getGradlePluginRepositoryCredentialId();

        Secret secretKey = Optional.ofNullable(accessKeyCredentialId)
                .map(it -> CredentialsProvider.findCredentialById(it, StringCredentials.class, run))
                .map(StringCredentials::getSecret)
                .orElse(null);
        Secret secretPassword = Optional.ofNullable(gradlePluginRepositoryCredentialId)
                .map(it -> CredentialsProvider.findCredentialById(it, StandardUsernamePasswordCredentials.class, run))
                .map(StandardUsernamePasswordCredentials::getPassword)
                .orElse(null);

        if (secretKey == null && secretPassword == null) {
            return;
        }
        DevelocityLogger logger = new DevelocityLogger(listener);

        Secret shortLivedToken = getShortLivedToken(secretKey, logger);

        run.addAction(DevelocityParametersAction.of(logger, shortLivedToken, secretPassword));
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    private @Nullable Secret getShortLivedToken(Secret secretKey, DevelocityLogger logger) {
        if (secretKey == null) {
            return null;
        }
        if (!DevelocityAccessCredentials.isValid(secretKey.getPlainText())) {
            logger.error("Develocity access key format is not valid");
            return null;
        }
        DevelocityAccessCredentials allKeys = DevelocityAccessCredentials.parse(secretKey.getPlainText());
        if (allKeys.isEmpty()) {
            return null;
        }
        String serverUrl = InjectionConfig.get().getServer();

        // If we know the URL or there's only one access key configured corresponding to the right URL
        if (InjectionConfig.get().isEnforceUrl() || allKeys.isSingleKey()) {
            String hostname = getHostnameFromServerUrl(serverUrl);
            if (hostname == null) {
                logger.error("Could not extract hostname from Develocity server URL");
                return null;
            }
            return allKeys.find(hostname)
                    .map(k ->
                            tokenClient.get(serverUrl, k, InjectionConfig.get().getShortLivedTokenExpiry()))
                    .filter(Optional::isPresent)
                    .map(k -> Secret.fromString(k.get().getRaw()))
                    .orElse(null);
        }

        // We're not sure exactly which DV URL will be effectively used so as best effort:
        // let's translate all access keys to short-lived tokens
        List<DevelocityAccessCredentials.HostnameAccessKey> shortLivedTokens = allKeys.stream()
                .map(k -> tokenClient.get("https://" + k.getHostname(), k, InjectionConfig.get().getShortLivedTokenExpiry()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        return shortLivedTokens.isEmpty() ? null : Secret.fromString(DevelocityAccessCredentials.of(shortLivedTokens).getRaw());
    }

    private String getHostnameFromServerUrl(String serverUrl) {
        try {
            return new URL(serverUrl).getHost();
        } catch (MalformedURLException e) {
            return null;
        }
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
