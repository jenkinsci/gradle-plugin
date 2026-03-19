package hudson.plugins.gradle;

import hudson.plugins.gradle.util.CollectionUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class SimpleBuildScanPublishedListener
    implements BuildScanPublishedListener, BuildScansAware, Serializable {

    private static final Logger LOGGER = Logger.getLogger(SimpleBuildScanPublishedListener.class.getName());

    private final List<String> buildScans = new ArrayList<>();

    @Override
    public void onBuildScanPublished(String scanUrl) {
        LOGGER.fine(() -> "Build scan has been published: " + scanUrl);
        buildScans.add(scanUrl);
    }

    @Override
    public List<String> getBuildScans() {
        return CollectionUtil.unmodifiableCopy(buildScans);
    }
}
