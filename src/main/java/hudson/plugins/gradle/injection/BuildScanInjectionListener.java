package hudson.plugins.gradle.injection;

import com.google.common.annotations.VisibleForTesting;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;
import jenkins.model.Jenkins;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class BuildScanInjectionListener extends ComputerListener {

    private static final Logger LOGGER = Logger.getLogger(BuildScanInjectionListener.class.getName());

    public static final String JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK = "JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK";

    private final List<BuildScanInjection> injectors;
    private final Supplier<EnvVars> globalEnvVarsSupplier;
    private final Supplier<Collection<Computer>> computersSupplier;
    private final Supplier<InjectionConfig> injectionConfigSupplier;

    public BuildScanInjectionListener() {
        this(
            Arrays.asList(new GradleBuildScanInjection(), new MavenBuildScanInjection()),
            new JenkinsGlobalEnvVars(),
            new JenkinsComputers(),
            new JenkinsInjectionConfig());
    }

    @VisibleForTesting
    BuildScanInjectionListener(List<BuildScanInjection> injectors,
                               Supplier<EnvVars> globalEnvVarsSupplier,
                               Supplier<Collection<Computer>> computersSupplier,
                               Supplier<InjectionConfig> injectionConfigSupplier) {
        this.injectors = injectors;
        this.globalEnvVarsSupplier = globalEnvVarsSupplier;
        this.computersSupplier = computersSupplier;
        this.injectionConfigSupplier = injectionConfigSupplier;
    }

    @Override
    public void onOnline(Computer computer, TaskListener listener) {
        try {
            EnvVars globalEnvVars = computer.buildEnvironment(listener);
            if (globalAutoInjectionCheckEnabled(globalEnvVars) && isFeatureDisabled()) {
                return;
            }

            inject(computer, globalEnvVars);
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
        EnvVars globalEnvVars = globalEnvVarsSupplier.get();
        if (globalAutoInjectionCheckEnabled(globalEnvVars) && isFeatureDisabled()) {
            return;
        }

        for (Computer computer : computersSupplier.get()) {
            if (computer.isOnline()) {
                inject(computer, globalEnvVars);
            }
        }
    }

    private void inject(Computer computer, EnvVars globalEnvVars) {
        try {
            Node node = computer.getNode();
            EnvVars computerEnvVars = computer.getEnvironment();

            for (BuildScanInjection injector : injectors) {
                injector.inject(node, globalEnvVars, computerEnvVars);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error while build scans injection on " + computer.getName(), e);
        }
    }

    private boolean isFeatureDisabled() {
        InjectionConfig injectionConfig = injectionConfigSupplier.get();
        return !injectionConfig.isEnabled();
    }

    private static boolean globalAutoInjectionCheckEnabled(EnvVars envVars) {
        return EnvUtil.getEnv(envVars, JENKINSGRADLEPLUGIN_GLOBAL_AUTO_INJECTION_CHECK) != null;
    }

    private static final class JenkinsGlobalEnvVars implements Supplier<EnvVars> {

        private JenkinsGlobalEnvVars() {
        }

        @Override
        public EnvVars get() {
            return EnvUtil.globalEnvironment();
        }
    }

    private static final class JenkinsComputers implements Supplier<Collection<Computer>> {

        private JenkinsComputers() {
        }

        @Override
        public Collection<Computer> get() {
            return Arrays.asList(Jenkins.get().getComputers());
        }
    }

    private static final class JenkinsInjectionConfig implements Supplier<InjectionConfig> {

        private JenkinsInjectionConfig() {
        }

        @Override
        public InjectionConfig get() {
            return InjectionConfig.get();
        }
    }
}
