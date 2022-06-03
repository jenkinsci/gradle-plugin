package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.model.Node;

public interface BuildScanInjection {

    default String getEnv(EnvVars env, String key) {
        return env != null ? env.get(key) : null;
    }

    default boolean isEnabled(EnvVars env) {
        return getEnv(env, getActivationEnvironmentVariableName()) != null;
    }

    String getActivationEnvironmentVariableName();

    void inject(Node node, EnvVars envGlobal, EnvVars envComputer);
}
