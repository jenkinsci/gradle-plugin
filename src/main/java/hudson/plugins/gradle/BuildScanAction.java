package hudson.plugins.gradle;

import hudson.model.Action;

public class BuildScanAction implements Action {

    private final String scanUrl;

    public BuildScanAction(String scanUrl) {
        this.scanUrl = scanUrl;
    }

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

    public String getScanUrl() {
        return scanUrl;
    }
}
