package hudson.plugins.gradle;

import hudson.model.Action;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ExportedBean
public class BuildScanAction implements Action {

    // Backward compatibility for old plugins versions which created an action per-scan
    private transient String scanUrl;

    private List<String> scanUrls = new ArrayList<>();
    private Map<String, List<String>> scanUrlsPerStage = new LinkedHashMap<>();

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

    @Exported
    public Map<String, List<String>> getScanUrlsPerStage() {
        return scanUrlsPerStage;
    }

    private Object readResolve() {
        if (scanUrl != null) {
            scanUrls = Collections.singletonList(scanUrl);
        }

        return this;
    }

    public void addScanUrl(String stage, String scanUrl) {
        if (stage == null) {
            scanUrls.add(scanUrl);
        } else {
            scanUrlsPerStage.compute(stage, (key, previousValue) -> {
                List<String> urls = previousValue == null ? new ArrayList<>() : previousValue;
                if (!urls.contains(scanUrl)) {
                    urls.add(scanUrl);
                }
                return urls;
            });
        }
    }
}
