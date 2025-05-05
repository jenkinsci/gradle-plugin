package hudson.plugins.gradle;

import hudson.plugins.gradle.util.CollectionUtil;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public final class SimpleBuildScanPublishedListener
        implements BuildScanPublishedListener, BuildScansAware, Serializable {

    private final List<String> buildScans = new ArrayList<>();

    @Override
    public void onBuildScanPublished(String scanUrl) {
        buildScans.add(scanUrl);
    }

    @Override
    public List<String> getBuildScans() {
        return CollectionUtil.unmodifiableCopy(buildScans);
    }
}
