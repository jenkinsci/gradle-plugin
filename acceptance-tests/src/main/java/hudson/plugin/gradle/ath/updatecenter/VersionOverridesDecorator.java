package hudson.plugin.gradle.ath.updatecenter;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.test.acceptance.update_center.PluginMetadata;
import org.jenkinsci.test.acceptance.update_center.UpdateCenterMetadata;

import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class VersionOverridesDecorator implements Consumer<UpdateCenterMetadata> {

    private static final Logger LOGGER = Logger.getLogger(VersionOverridesDecorator.class.getName());

    public static final String PLUGIN_VERSION_OVERRIDES = "hudson.plugin.gradle.pluginVersionOverrides";

    @Override
    public void accept(UpdateCenterMetadata ucm) {
        String overrides = System.getProperty(PLUGIN_VERSION_OVERRIDES);
        if (StringUtils.isBlank(overrides)) {
            return;
        }

        for (String override : overrides.split(",")) {
            String[] chunks = override.split("=");
            if (chunks.length != 2) {
                throw new IllegalStateException("Unable to parse " + overrides + " as a " + PLUGIN_VERSION_OVERRIDES);
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
