package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.PasswordParameterValue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.gradle.GradleEnterpriseLogger;
import hudson.util.Secret;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Extension
public class BuildScanEnvironmentContributor extends EnvironmentContributor {

    @Override
    public void buildEnvironmentFor(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull TaskListener listener) {
        Secret secret = InjectionConfig.get().getAccessKey();
        if (secret == null || alreadyExecuted(run)) {
            return;
        }

        String accessKey = secret.getPlainText();
        if (!GradleEnterpriseAccessKeyValidator.getInstance().isValid(accessKey)) {
            GradleEnterpriseLogger logger = new GradleEnterpriseLogger(listener);
            logger.error("Gradle Enterprise access key format is not valid");
            run.addAction(GradleEnterpriseParametersAction.empty());
            return;
        }

        run.addAction(GradleEnterpriseParametersAction.of(accessKey));
    }

    private static boolean alreadyExecuted(@Nonnull Run run) {
        return run.getAction(GradleEnterpriseParametersAction.class) != null;
    }

    public static class GradleEnterpriseParametersAction extends ParametersAction {

        private static final String GRADLE_ENTERPRISE_ACCESS_KEY = "GRADLE_ENTERPRISE_ACCESS_KEY";
        private static final Set<String> ADDITIONAL_SAFE_PARAMETERS = Collections.singleton(GRADLE_ENTERPRISE_ACCESS_KEY);

        private static final GradleEnterpriseParametersAction EMPTY = new GradleEnterpriseParametersAction();

        GradleEnterpriseParametersAction(List<ParameterValue> parameters, Collection<String> additionalSafeParameters) {
            super(parameters, additionalSafeParameters);
        }

        GradleEnterpriseParametersAction() {
            super(Collections.emptyList());
        }

        static GradleEnterpriseParametersAction empty() {
            return EMPTY;
        }

        static GradleEnterpriseParametersAction of(String accessKey) {
            return new GradleEnterpriseParametersAction(
                Collections.singletonList(new PasswordParameterValue(GRADLE_ENTERPRISE_ACCESS_KEY, accessKey, null)),
                ADDITIONAL_SAFE_PARAMETERS
            );
        }
    }
}
