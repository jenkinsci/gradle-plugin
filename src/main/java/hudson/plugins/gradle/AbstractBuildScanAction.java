package hudson.plugins.gradle;

import hudson.model.Action;
import hudson.model.Actionable;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@ExportedBean
public abstract class AbstractBuildScanAction implements Action {

    // Backward compatibility for old plugins versions which created an action per-scan
    private transient String scanUrl;
    // Backward compatibility for old plugins versions which created an List of Strings:
    private transient List<String> scanUrls = new ArrayList<>();

    protected transient Actionable target;

    private List<BuildScan> buildScans = new ArrayList<>();

    @Override
    public String getIconFileName() {
        return "/plugin/gradle/images/svgs/gradle-build-scan.svg";
    }

    @Override
    public String getDisplayName() {
        return "Build Scan";
    }

    @Override
    public String getUrlName() {
        return "buildScan";
    }

    public void addScanUrl(String scanUrl) {
        addScanUrl(scanUrl, null);
    }

    public void addScanUrl(String scanUrl, String scanLabel) {
        BuildScan buildScan = new BuildScan(scanUrl, scanLabel);
        if (!buildScans.contains(buildScan)) {
            buildScans.add(buildScan);
        }
    }

    @Exported
    public List<BuildScan> getBuildScans() {
        return Collections.unmodifiableList(buildScans);
    }

    protected Object readResolve() {
        if (scanUrl != null) {
            buildScans = Collections.singletonList(new BuildScan(scanUrl));
        }

        if (scanUrls != null && !scanUrls.isEmpty()) {
            buildScans = scanUrls
                            .stream()
                            .map(it -> new BuildScan(it))
                            .collect(Collectors.toList());
        }

        return this;
    }

    public Actionable getTarget() {
        return target;
    }

}
