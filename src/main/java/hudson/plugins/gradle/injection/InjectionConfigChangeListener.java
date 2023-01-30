package hudson.plugins.gradle.injection;

import com.google.common.annotations.VisibleForTesting;
import hudson.EnvVars;
import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Computer;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import jenkins.model.Jenkins;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * Performs build scans auto-injection/cleanup when the {@link InjectionConfig} changes.
 */
@Extension
public class InjectionConfigChangeListener extends SaveableListener {

    private final GradleEnterpriseInjector injector;
    private final Supplier<EnvVars> globalEnvVarsSupplier;
    private final Supplier<Collection<Computer>> computersSupplier;

    public InjectionConfigChangeListener() {
        this(
            new GradleEnterpriseInjector(),
            new JenkinsGlobalEnvVars(),
            new JenkinsComputers()
        );
    }

    @VisibleForTesting
    InjectionConfigChangeListener(GradleEnterpriseInjector injector,
                                  Supplier<EnvVars> globalEnvVarsSupplier,
                                  Supplier<Collection<Computer>> computersSupplier) {
        this.injector = injector;
        this.globalEnvVarsSupplier = globalEnvVarsSupplier;
        this.computersSupplier = computersSupplier;
    }

    @Override
    public void onChange(Saveable saveable, XmlFile file) {
        if (saveable instanceof InjectionConfig) {
            InjectionConfig injectionConfig = (InjectionConfig) saveable;

            EnvVars globalEnvVars = globalEnvVarsSupplier.get();
            if (InjectionUtil.globalAutoInjectionCheckEnabled(globalEnvVars) && injectionConfig.isDisabled()) {
                return;
            }

            for (Computer computer : computersSupplier.get()) {
                if (computer.isOnline()) {
                    injector.inject(computer, globalEnvVars);
                }
            }
        }
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
}
