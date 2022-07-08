package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.model.Node;

public interface BuildScanInjection {

    default boolean isOn(EnvVars env) {
        return EnvUtil.getEnv(env, getActivationEnvironmentVariableName()) != null;
    }

    String getActivationEnvironmentVariableName();

    void inject(Node node, EnvVars envGlobal, EnvVars envComputer);
}
