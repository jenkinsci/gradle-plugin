package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerListener;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

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

            if (injectionEnabled(envGlobal)) {
                inject(computer, envGlobal);
            }
        } catch (Throwable t) {
            /**
             * We should catch everything because this is not handled by {@link hudson.slaves.SlaveComputer#setChannel(Channel, OutputStream, Channel.Listener)}
             * and handle it the same way as Jenkins.
             */

            if (t instanceof Error) {
                // We propagate Runtime errors, because they are fatal.
                throw (Error) t;
            }

            LOGGER.log(WARNING, "Invocation of onOnline failed for " + computer.getName(), t);
        }
    }

    @Override
    public void onConfigurationChange() {
        EnvVars envGlobal = getGlobalEnv();

        if (injectionEnabled(envGlobal)) {
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

    private static boolean injectionEnabled(EnvVars env) {
        return EnvUtil.isSet(env, FEATURE_TOGGLE_INJECTION);
    }
}
