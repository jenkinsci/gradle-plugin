package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Computer;
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
    private static final boolean DISABLED = Boolean.getBoolean(BuildScanInjectionListener.class.getName() + ".disabled");

    private final List<BuildScanInjection> injections = Arrays.asList(
            new GradleBuildScanInjection(),
            new MavenBuildScanInjection()
    );

    @Override
    public void onOnline(Computer c, TaskListener listener) {
        if (DISABLED) {
            return;
        }
        try {
            EnvVars envGlobal = c.buildEnvironment(listener);
            EnvVars envComputer = c.getEnvironment();

            LOGGER.fine("onOnline " + c.getName());
            inject(c, envGlobal, envComputer);
        } catch (IOException | InterruptedException e) {
            LOGGER.warning("Error processing scan injection - " + e.getMessage());
        }
    }

    @Override
    public void onConfigurationChange() {
        if (DISABLED) {
            return;
        }
        EnvironmentVariablesNodeProperty envProperty = Jenkins.get().getGlobalNodeProperties()
                .get(EnvironmentVariablesNodeProperty.class);
        EnvVars envGlobal = envProperty != null ? envProperty.getEnvVars() : null;

        for (Computer c : Jenkins.get().getComputers()) {
            try {
                LOGGER.fine("onConfigurationChange " + c.getName());
                final EnvVars envComputer = c.getEnvironment();
                inject(c, envGlobal, envComputer);
            } catch (IOException | InterruptedException e) {
                LOGGER.warning("Error processing scan injection - " + e.getMessage());
            }
        }
    }

    private void inject(Computer c, EnvVars envGlobal, EnvVars envComputer) {
        injections.forEach(injection -> injection.inject(c.getNode(), envGlobal, envComputer));
    }

}
