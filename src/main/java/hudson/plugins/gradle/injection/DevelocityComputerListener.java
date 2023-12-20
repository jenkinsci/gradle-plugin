package hudson.plugins.gradle.injection;

import com.google.common.annotations.VisibleForTesting;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Performs build scans auto-injection/cleanup when a {@link Computer} comes online.
 *
 * @see InjectionConfig
 */
@Extension
public class DevelocityComputerListener extends ComputerListener {

    private static final Logger LOGGER = Logger.getLogger(DevelocityComputerListener.class.getName());

    private final DevelocityInjector injector;
    private final Supplier<InjectionConfig> injectionConfigSupplier;

    public DevelocityComputerListener() {
        this(new DevelocityInjector(), new JenkinsInjectionConfig());
    }

    @VisibleForTesting
    DevelocityComputerListener(DevelocityInjector injector,
                               Supplier<InjectionConfig> injectionConfigSupplier) {
        this.injector = injector;
        this.injectionConfigSupplier = injectionConfigSupplier;
    }

    @Override
    public void onOnline(Computer computer, TaskListener listener) {
        try {
            EnvVars globalEnvVars = computer.buildEnvironment(listener);
            if (InjectionUtil.globalAutoInjectionCheckEnabled(globalEnvVars) && isFeatureDisabled()) {
                return;
            }

            injector.inject(computer, globalEnvVars);
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

    private boolean isFeatureDisabled() {
        InjectionConfig injectionConfig = injectionConfigSupplier.get();
        return injectionConfig.isDisabled();
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
