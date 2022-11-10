package hudson.plugin.gradle.ath.updatecenter;

import com.cloudbees.sdk.extensibility.Extension;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.test.acceptance.update_center.PluginMetadata;
import org.jenkinsci.test.acceptance.update_center.UpdateCenterMetadata;
import org.jenkinsci.test.acceptance.update_center.UpdateCenterMetadataDecorator;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class PluginVersionOverrideUpdateCenterMetadataDecorator implements UpdateCenterMetadataDecorator {

    private static final Logger LOGGER = Logger.getLogger(PluginVersionOverrideUpdateCenterMetadataDecorator.class.getName());

    @Override
    public void decorate(UpdateCenterMetadata ucm) {
        LOGGER.log(Level.INFO, "Checking for plugin version overrides...");
        String overrides = System.getProperty(WithVersionOverrides.PLUGIN_VERSION_OVERRIDES);
        if (StringUtils.isBlank(overrides)) {
            return;
        }

        for (String override : overrides.split(",")) {
            String[] chunks = override.split("=");
            if (chunks.length != 2) {
                throw new IllegalStateException(
                    "Unable to parse " + overrides + " as a " + WithVersionOverrides.PLUGIN_VERSION_OVERRIDES);
            }
            override(ucm, chunks[0], chunks[1]);
        }
    }

    private void override(UpdateCenterMetadata ucm, String name, String version) {
        Map<String, PluginMetadata> plugins = ucm.plugins;

        PluginMetadata original = plugins.get(name);
        if (original == null) {
            throw new IllegalArgumentException("Plugin does not exists in update center: " + name);
        }
        plugins.put(name, original.withVersion(version));

        LOGGER.log(
            Level.INFO,
            "Overriding the version of {0} with {1}",
            new String[]{name, version}
        );
    }
}
