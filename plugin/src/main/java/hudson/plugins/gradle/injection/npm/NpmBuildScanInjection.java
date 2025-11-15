package hudson.plugins.gradle.injection.npm;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Node;
import hudson.plugins.gradle.injection.ArtifactDigest;
import hudson.plugins.gradle.injection.EnvUtil;
import hudson.plugins.gradle.injection.EnvVar;
import hudson.plugins.gradle.injection.InjectionConfig;
import hudson.plugins.gradle.injection.InjectionUtil;
import hudson.remoting.RemoteInputStream;
import jenkins.model.Jenkins;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.plugins.gradle.injection.InjectionUtil.HOME;

public class NpmBuildScanInjection implements NpmInjectionAware {

    private static final Logger LOGGER = Logger.getLogger(NpmBuildScanInjection.class.getName());

    public static final String DEVELOCITY_INTERNAL_DISABLE_AGENT = "DEVELOCITY_INTERNAL_DISABLE_AGENT";

    private enum NpmAgentConfig implements EnvVar {
        DEVELOCITY_URL,
        DEVELOCITY_ALLOW_UNTRUSTED_SERVER,
        DEVELOCITY_INTERNAL_ENABLE_JEST_REPORTER_INJECTION,
        DEVELOCITY_VALUE_CI_AUTO_INJECTION("DEVELOCITY_VALUE_CIAutoInjection"),
        NODE_OPTIONS;

        @Nullable
        private final String envVar;

        NpmAgentConfig() {
            this(null);
        }

        NpmAgentConfig(@Nullable String envVar) {
            this.envVar = envVar;
        }

        @Override
        public String getEnvVar() {
            return envVar != null ? envVar : name();
        }
    }

    public void inject(@Nullable Node node, @Nullable ArtifactDigest npmAgentDigest, EnvVars envComputer) {
        if (node == null) {
            return;
        }

        FilePath userHome = getUserHome(node, envComputer).orElse(null);
        if (userHome == null) {
            LOGGER.log(Level.WARNING, "Could not determine user home");
            return;
        }

        InjectionConfig config = InjectionConfig.get();
        boolean enabled = isInjectionEnabledForNode(config, node);
        try {
            if (enabled) {
                if (npmAgentDigest != null) {
                    inject(node, userHome, npmAgentDigest, config);
                } else {
                    LOGGER.log(Level.WARNING, "npm agent digest is not present even though injection is enabled");
                }
            } else {
                cleanup(node, userHome);
            }
        } catch (IllegalStateException e) {
            if (enabled) {
                LOGGER.log(Level.WARNING, "Unexpected exception while injecting build scans for npm", e);
            }
        }
    }

    private void inject(Node node, FilePath userHome, ArtifactDigest npmAgentDigest, InjectionConfig config) {
        LOGGER.log(Level.INFO, "Injecting npm agent version {0}", config.getNpmAgentVersion());

        try {
            FilePath controllerRootPath = Jenkins.get().getRootPath();

            installNpmAgent(controllerRootPath, userHome, npmAgentDigest, config);
            injectEnvironmentVariables(node, config);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private void installNpmAgent(FilePath controllerRootPath, FilePath userHome, ArtifactDigest npmAgentDigest, InjectionConfig config) throws IOException, InterruptedException {
        FilePath scopePath = userHome.child(".node_modules").child("@gradle-tech");
        FilePath agentPath = scopePath.child("develocity-agent");
        // ~/.node_libraries/@gradle-tech/develocity-agent/version.meta
        FilePath versionMeta = agentPath.child("version.meta");
        if (!npmAgentChanged(versionMeta, npmAgentDigest)) {
            return;
        }
        scopePath.deleteRecursive();
        // Unarchive the agent tarball from the controller to the agent node
        FilePath agentFilePath = controllerRootPath.child(InjectionUtil.DOWNLOAD_CACHE_DIR).child(NpmAgentDownloadHandler.AGENT_FILENAME);
        try (InputStream in = agentFilePath.read()) {
            UnarchiveNpmAgent action = new UnarchiveNpmAgent(config.getNpmAgentVersion(), new RemoteInputStream(in, RemoteInputStream.Flag.GREEDY));
            agentPath.act(action);
        }
        versionMeta.write(npmAgentDigest.digest(), StandardCharsets.UTF_8.name());
    }

    private void injectEnvironmentVariables(Node node, InjectionConfig config) {
        EnvUtil.setEnvVar(node, NpmAgentConfig.DEVELOCITY_URL, config.getServer());
        if (config.isAllowUntrusted()) {
            EnvUtil.setEnvVar(node, NpmAgentConfig.DEVELOCITY_ALLOW_UNTRUSTED_SERVER, "true");
        } else {
            EnvUtil.removeEnvVar(node, NpmAgentConfig.DEVELOCITY_ALLOW_UNTRUSTED_SERVER);
        }
        EnvUtil.setEnvVar(node, NpmAgentConfig.DEVELOCITY_INTERNAL_ENABLE_JEST_REPORTER_INJECTION, "true");
        EnvUtil.setEnvVar(node, NpmAgentConfig.DEVELOCITY_VALUE_CI_AUTO_INJECTION, "Jenkins");
        // TODO: Merge with existing NODE_OPTIONS if present
        EnvUtil.setEnvVar(node, NpmAgentConfig.NODE_OPTIONS, "-r @gradle-tech/develocity-agent/preload");
    }

    private boolean npmAgentChanged(FilePath versionMeta, ArtifactDigest npmAgentDigest) throws IOException, InterruptedException {
        if (!versionMeta.exists()) {
            return true;
        }
        String currentDigest = versionMeta.readToString();
        return !npmAgentDigest.matches(currentDigest);
    }

    private void cleanup(Node node, FilePath userHome) {
        try {
            FilePath scopePath = userHome.child(".node_modules").child("@gradle-tech");
            scopePath.deleteRecursive();

            EnvUtil.removeEnvVars(node, NpmAgentConfig.values());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Optional<FilePath> getUserHome(Node node, EnvVars envComputer) {
        return Optional.ofNullable(EnvUtil.getEnv(envComputer, HOME)).map(node::createPath);
    }
}
