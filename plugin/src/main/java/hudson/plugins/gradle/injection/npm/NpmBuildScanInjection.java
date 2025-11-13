package hudson.plugins.gradle.injection.npm;

import hudson.EnvVars;
import hudson.model.Node;
import hudson.plugins.gradle.injection.ArtifactDigest;

import javax.annotation.Nullable;
import java.util.logging.Logger;

public class NpmBuildScanInjection implements NpmInjectionAware {

    private static final Logger LOGGER = Logger.getLogger(NpmBuildScanInjection.class.getName());

    public void inject(@Nullable Node node, @Nullable ArtifactDigest npmAgentDigest, EnvVars envComputer) {
        if (node == null) {
            return;
        }
        // TODO: Implement npm build scan injection logic
    }
}
