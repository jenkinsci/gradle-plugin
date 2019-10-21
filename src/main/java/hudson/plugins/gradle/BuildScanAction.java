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
    private static final String UNDEFINED_STAGE = "UNDEFINED";
    private static final String UNDEFINED_PARALLEL_BRANCH = "UNDEFINED";

    // Backward compatibility for old plugins versions which created an action per-scan
    private transient String scanUrl;

    private List<String> scanUrls = new ArrayList<>();
    private Map<String, Map<String, List<String>>> scanUrlsPerStagePerParallelBranch = new LinkedHashMap<>();

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
    public Map<String, Map<String, List<String>>> getScanUrlsPerStagePerParallelBranch() {
        return scanUrlsPerStagePerParallelBranch;
    }

    private Object readResolve() {
        if (scanUrl != null) {
            scanUrls = Collections.singletonList(scanUrl);
        }

        return this;
    }

    public void addScanUrl(String stage, String scanUrl, String parallelBranch) {
        if (stage == null && parallelBranch == null) {
            scanUrls.add(scanUrl);
        } else {
            scanUrlsPerStagePerParallelBranch.compute(stage == null ? UNDEFINED_STAGE : stage, (key, previousValue) -> {
                Map<String, List<String>> perBranch = previousValue == null ? new LinkedHashMap<>() : previousValue;
                perBranch.compute(parallelBranch == null ? UNDEFINED_PARALLEL_BRANCH : parallelBranch, (branchKey, previousScans) -> {
                    List<String> urls = previousScans == null ? new ArrayList<>() : previousScans;
                    if (!urls.contains(scanUrl)) {
                        urls.add(scanUrl);
                    }
                    return urls;
                });
                return perBranch;
            });
        }
    }
}
