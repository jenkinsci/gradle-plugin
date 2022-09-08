package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

@Extension
public class BuildScanInjectionListener extends ComputerListener {

    private static final Logger LOGGER = Logger.getLogger(BuildScanInjectionListener.class.getName());

    private static final String FEATURE_TOGGLE_INJECTION = "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_INJECTION";

    private final List<BuildScanInjection> injections =
        Arrays.asList(new GradleBuildScanInjection(), new MavenBuildScanInjection());

    @Override
    public void onOnline(Computer computer, TaskListener listener) {
        try {
            EnvVars envGlobal = computer.buildEnvironment(listener);

            if (isInjectionEnabled(envGlobal)) {
                inject(computer, envGlobal);
            }
        } catch (IOException | InterruptedException e) {
            // nothing can be done
        }
    }

    @Override
    public void onConfigurationChange() {
        EnvVars envGlobal = getGlobalEnv();

        if (isInjectionEnabled(envGlobal)) {
            for (Computer computer : Jenkins.get().getComputers()) {
                inject(computer, envGlobal);
            }
        }
    }

    private void inject(Computer computer, EnvVars envGlobal) {
        try {
            Node node = computer.getNode();
            EnvVars envComputer = computer.getEnvironment();

            injections.forEach(injection -> injection.inject(node, envGlobal, envComputer));
        } catch (IOException | InterruptedException e) {
            LOGGER.info("Error processing scan injection - {}" + e.getMessage());
        }
    }

    private static EnvVars getGlobalEnv() {
        EnvironmentVariablesNodeProperty envProperty =
            Jenkins.get().getGlobalNodeProperties().get(EnvironmentVariablesNodeProperty.class);

        return envProperty != null ? envProperty.getEnvVars() : null;
    }

    private static boolean isInjectionEnabled(EnvVars env) {
        return EnvUtil.isSet(env, FEATURE_TOGGLE_INJECTION);
    }
}
