package hudson.plugins.gradle.injection;

import com.google.common.annotations.VisibleForTesting;
import hudson.EnvVars;
import hudson.model.Computer;
import hudson.model.Node;

import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DevelocityInjector {

    private static final Logger LOGGER = Logger.getLogger(DevelocityInjector.class.getName());

    private final Collection<BuildScanInjection> injectors;

    public DevelocityInjector() {
        this(new GradleBuildScanInjection(), new MavenBuildScanInjection());
    }

    @VisibleForTesting
    DevelocityInjector(BuildScanInjection... injectors) {
        this.injectors = Arrays.asList(injectors);
    }

    public void inject(Computer computer, EnvVars globalEnvVars) {
        try {
            Node node = computer.getNode();
            EnvVars computerEnvVars = computer.getEnvironment();

            for (BuildScanInjection injector : injectors) {
                injector.inject(node, globalEnvVars, computerEnvVars);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error injecting build scans on " + computer.getName(), e);
        }
    }
}
