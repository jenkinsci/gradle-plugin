package hudson.plugins.gradle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
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
        if (buildScans.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(buildScans));
    }
}
