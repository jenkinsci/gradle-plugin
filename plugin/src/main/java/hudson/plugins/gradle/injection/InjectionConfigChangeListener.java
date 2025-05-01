package hudson.plugins.gradle.injection;

import com.google.common.annotations.VisibleForTesting;
import hudson.EnvVars;
import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import jenkins.model.Jenkins;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Performs build scans auto-injection/cleanup when the {@link InjectionConfig} changes.
 */
@Extension
public class InjectionConfigChangeListener extends SaveableListener {

    private static final Logger LOGGER = Logger.getLogger(InjectionConfigChangeListener.class.getName());

    private final GradleBuildScanInjection gradleBuildScanInjection;
    private final MavenBuildScanInjection mavenBuildScanInjection;
    private final MavenExtensionDownloadHandler mavenExtensionDownloadHandler;
    private final Supplier<EnvVars> globalEnvVarsSupplier;
    private final Supplier<Collection<Computer>> computersSupplier;

    public InjectionConfigChangeListener() {
        this(
                new GradleBuildScanInjection(),
                new MavenBuildScanInjection(),
                new MavenExtensionDownloadHandler(),
                new JenkinsGlobalEnvVars(),
                new JenkinsComputers()
        );
    }

    @VisibleForTesting
    InjectionConfigChangeListener(
            GradleBuildScanInjection gradleBuildScanInjection,
            MavenBuildScanInjection mavenBuildScanInjection,
            MavenExtensionDownloadHandler mavenExtensionDownloadHandler,
            Supplier<EnvVars> globalEnvVarsSupplier,
            Supplier<Collection<Computer>> computersSupplier
    ) {
        this.gradleBuildScanInjection = gradleBuildScanInjection;
        this.mavenBuildScanInjection = mavenBuildScanInjection;
        this.mavenExtensionDownloadHandler = mavenExtensionDownloadHandler;
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

            try {
                Map<MavenExtension, String> extensionsDigest = mavenExtensionDownloadHandler.ensureExtensionsDownloaded(
                        () -> Jenkins.get().getRootDir(), injectionConfig
                );

                for (Computer computer : computersSupplier.get()) {
                    if (computer.isOnline()) {
                        Node node = computer.getNode();
                        EnvVars computerEnvVars = computer.getEnvironment();

                        gradleBuildScanInjection.inject(node, globalEnvVars, computerEnvVars);
                        mavenBuildScanInjection.inject(node, extensionsDigest);
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Invocation of onChange failed", e);
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
