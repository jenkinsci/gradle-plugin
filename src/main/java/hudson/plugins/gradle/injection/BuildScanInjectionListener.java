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

    private static final String FEATURE_TOGGLE_INJECTION = "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_INJECTION";

    private final List<BuildScanInjection> injections = Arrays.asList(
            new GradleBuildScanInjection(),
            new MavenBuildScanInjection()
    );

    private boolean isInjectionEnabled(EnvVars env) {
        return EnvUtil.getEnv(env, FEATURE_TOGGLE_INJECTION) != null;
    }

    @Override
    public void onOnline(Computer c, TaskListener listener) {
        try {
            EnvVars envGlobal = c.buildEnvironment(listener);
            if(isInjectionEnabled(envGlobal)) {
                try {
                    EnvVars envComputer = c.getEnvironment();

                    inject(c, envGlobal, envComputer);
                } catch (IOException | InterruptedException e) {
                    LOGGER.info("Error processing scan injection - " + e.getMessage());
                }
            }
        } catch (IOException | InterruptedException e) {
            // nothing can be done
        }
    }

    @Override
    public void onConfigurationChange() {
        EnvironmentVariablesNodeProperty envProperty = Jenkins.get().getGlobalNodeProperties()
                .get(EnvironmentVariablesNodeProperty.class);
        EnvVars envGlobal = envProperty != null ? envProperty.getEnvVars() : null;

        if(isInjectionEnabled(envGlobal)) {
            for (Computer c : Jenkins.get().getComputers()) {
                try {
                    final EnvVars envComputer = c.getEnvironment();
                    inject(c, envGlobal, envComputer);
                } catch (IOException | InterruptedException e) {
                    LOGGER.info("Error processing scan injection - " + e.getMessage());
                }
            }
        }
    }

    private void inject(Computer c, EnvVars envGlobal, EnvVars envComputer) {
        injections.forEach(injection -> injection.inject(c.getNode(), envGlobal, envComputer));
    }

}
