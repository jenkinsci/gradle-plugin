package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class BuildScanInjectionListener extends ComputerListener {

    private static final Logger LOGGER = Logger.getLogger(BuildScanInjectionListener.class.getName());

    private final List<BuildScanInjection> injections =
        Arrays.asList(new GradleBuildScanInjection(), new MavenBuildScanInjection());

    @Override
    public void onOnline(Computer computer, TaskListener listener) {
        try {
            if (isFeatureEnabled()) {
                EnvVars envGlobal = computer.buildEnvironment(listener);

                inject(computer, envGlobal);
            }
        } catch (Throwable t) {
            /*
             * We should catch everything because this is not handled by {@link hudson.slaves.SlaveComputer#setChannel(Channel, OutputStream, Channel.Listener)}
             * and handle it the same way as Jenkins.
             */
            if (t instanceof Error) {
                // We propagate Runtime errors, because they are fatal.
                throw (Error) t;
            }

            LOGGER.log(Level.WARNING, "Invocation of onOnline failed for " + computer.getName(), t);
        }
    }

    @Override
    public void onConfigurationChange() {
        if (isFeatureEnabled()) {
            EnvVars envGlobal = EnvUtil.globalEnvironment();

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
            LOGGER.log(Level.WARNING, "Error while build scans injection on " + computer.getName(), e);
        }
    }

    private static boolean isFeatureEnabled() {
        return InjectionConfig.get().isEnabled();
    }
}
