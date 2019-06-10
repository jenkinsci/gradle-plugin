package hudson.plugins.gradle;

import hudson.model.Action;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ExportedBean
public class BuildScanAction implements Action {

    // Backward compatibility for old plugins versions which created an action per-scan
    private transient String scanUrl;

    private List<String> scanUrls = new ArrayList<>();

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "Build Scan";
    }

    @Override
    public String getUrlName() {
        return null;
    }

    public void addScanUrl(String scanUrl) {
        if (!scanUrls.contains(scanUrl)) {
            scanUrls.add(scanUrl);
        }
    }

    @Exported
    public List<String> getScanUrls() {
        return Collections.unmodifiableList(scanUrls);
    }

    private Object readResolve() {
        if (scanUrl != null) {
            scanUrls = Collections.singletonList(scanUrl);
        }

        return this;
    }
}
