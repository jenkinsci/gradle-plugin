package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.model.Node;

import java.util.Locale;

public interface BuildScanInjection {

    String DISABLED = "off";

    default String getEnv(EnvVars env, String key) {
        return env != null ? env.get(key) : null;
    }

    default boolean isEnabled(EnvVars env) {
        return getEnv(env, getActivationEnvironmentVariableName()) != null;
    }

    default boolean isOn(EnvVars env) {
        return isEnabled(env) && !DISABLED.equals(getEnv(env, getActivationEnvironmentVariableName().toLowerCase(Locale.ROOT)));
    }

    String getActivationEnvironmentVariableName();

    void inject(Node node, EnvVars envGlobal, EnvVars envComputer);
}
