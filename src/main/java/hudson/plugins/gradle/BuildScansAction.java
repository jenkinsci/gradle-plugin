package hudson.plugins.gradle;

import hudson.model.Action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BuildScansAction implements Action {

    private final List<String> scanUrls = new ArrayList<>();

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "Build Scans";
    }

    @Override
    public String getUrlName() {
        return null;
    }

    public void addScanUrl(String scanUrl) {
        scanUrls.add(scanUrl);
    }

    public List<String> getScanUrls() {
        return Collections.unmodifiableList(scanUrls);
    }
}
