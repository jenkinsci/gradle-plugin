package hudson.plugins.gradle.injection;

import com.google.common.annotations.VisibleForTesting;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.plugins.gradle.injection.npm.NpmAgentDownloadHandler;
import hudson.plugins.gradle.injection.npm.NpmBuildScanInjection;
import hudson.slaves.ComputerListener;
import jenkins.model.Jenkins;

import java.io.File;
import java.util.Map;
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

    private final GradleBuildScanInjection gradleBuildScanInjection;
    private final MavenBuildScanInjection mavenBuildScanInjection;
    private final MavenExtensionDownloadHandler mavenExtensionDownloadHandler;
    private final NpmBuildScanInjection npmBuildScanInjection;
    private final NpmAgentDownloadHandler npmAgentDownloadHandler;
    private final Supplier<InjectionConfig> injectionConfigSupplier;

    @SuppressWarnings("unused")
    public DevelocityComputerListener() {
        this(
                new GradleBuildScanInjection(),
                new MavenBuildScanInjection(),
                new MavenExtensionDownloadHandler(),
                new NpmBuildScanInjection(),
                new NpmAgentDownloadHandler(),
                new JenkinsInjectionConfig()
        );
    }

    @VisibleForTesting
    DevelocityComputerListener(
            GradleBuildScanInjection gradleBuildScanInjection,
            MavenBuildScanInjection mavenBuildScanInjection,
            MavenExtensionDownloadHandler mavenExtensionDownloadHandler,
            NpmBuildScanInjection npmBuildScanInjection,
            NpmAgentDownloadHandler npmAgentDownloadHandler,
            Supplier<InjectionConfig> injectionConfigSupplier
    ) {
        this.gradleBuildScanInjection = gradleBuildScanInjection;
        this.mavenBuildScanInjection = mavenBuildScanInjection;
        this.mavenExtensionDownloadHandler = mavenExtensionDownloadHandler;
        this.npmBuildScanInjection = npmBuildScanInjection;
        this.npmAgentDownloadHandler = npmAgentDownloadHandler;
        this.injectionConfigSupplier = injectionConfigSupplier;
    }

    @Override
    public void onOnline(Computer computer, TaskListener listener) {
        try {
            InjectionConfig injectionConfig = injectionConfigSupplier.get();
            EnvVars globalEnvVars = computer.buildEnvironment(listener);
            if (InjectionUtil.globalAutoInjectionCheckEnabled(globalEnvVars) && injectionConfig.isDisabled()) {
                return;
            }

            Supplier<File> root = () -> Jenkins.get().getRootDir();
            // When the agent becomes online, all artifacts must be already downloaded on the controller.
            Map<MavenExtension, String> extensionsDigest = mavenExtensionDownloadHandler.getExtensionDigests(root, injectionConfig);
            ArtifactDigest npmAgentDigest =
                    npmBuildScanInjection
                            .ifInjectionEnabledGlobally(injectionConfig, () -> npmAgentDownloadHandler.getDownloadedNpmAgentDigest(root))
                            .orElse(null);

            Node node = computer.getNode();
            EnvVars computerEnvVars = computer.getEnvironment();

            gradleBuildScanInjection.inject(node, globalEnvVars, computerEnvVars);
            mavenBuildScanInjection.inject(node, extensionsDigest);
            npmBuildScanInjection.inject(node, npmAgentDigest, computerEnvVars);
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

    private static final class JenkinsInjectionConfig implements Supplier<InjectionConfig> {

        private JenkinsInjectionConfig() {
        }

        @Override
        public InjectionConfig get() {
            return InjectionConfig.get();
        }
    }
}
