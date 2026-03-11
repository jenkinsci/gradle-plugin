package hudson.plugins.gradle;

import hudson.plugins.gradle.util.CollectionUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SimpleBuildScanPublishedListener
    implements BuildScanPublishedListener, BuildScansAware, Serializable {

    private static final Logger LOGGER = Logger.getLogger(SimpleBuildScanPublishedListener.class.getName());

    private final List<String> buildScans = new ArrayList<>();

    @Override
    public void onBuildScanPublished(String scanUrl) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Build scan published: {0} (total so far: {1})", new Object[]{scanUrl, buildScans.size() + 1});
        }
        buildScans.add(scanUrl);
    }

    @Override
    public List<String> getBuildScans() {
        return CollectionUtil.unmodifiableCopy(buildScans);
    }
}
