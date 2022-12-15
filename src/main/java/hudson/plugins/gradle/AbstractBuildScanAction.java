package hudson.plugins.gradle;

import hudson.model.Action;
import hudson.model.Actionable;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ExportedBean
public abstract class AbstractBuildScanAction implements Action {

    // Backward compatibility for old plugins versions which created an action per-scan
    private transient String scanUrl;

    protected transient Actionable target;

    private List<String> scanUrls = new ArrayList<>();

    private List<ScanDetail> scanDetails = new ArrayList<>();

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
        if (!scanUrls.contains(scanUrl)) {
            scanUrls.add(scanUrl);
        }
    }

    public void addScanDetail(ScanDetail scanDetail) {
        if (!scanDetails.contains(scanDetail)) {
            scanDetails.add(scanDetail);
        }
    }

    @Exported
    public List<String> getScanUrls() {
        return Collections.unmodifiableList(scanUrls);
    }

    @Exported
    public List<ScanDetail> getScanDetails() {
        return Collections.unmodifiableList(scanDetails);
    }

    private Object readResolve() {
        if (scanUrl != null) {
            scanUrls = Collections.singletonList(scanUrl);
        }

        return this;
    }

    public Actionable getTarget() {
        return target;
    }
}
