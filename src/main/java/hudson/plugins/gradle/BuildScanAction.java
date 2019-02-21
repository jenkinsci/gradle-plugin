package hudson.plugins.gradle;

import hudson.model.Action;

/**
 * @deprecated This class remains only for backwards compatibility so that actions persisted for old builds will
 * continue to render correctly. At some point this should probably be removed.
 *
 * @see BuildScansAction
 */
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
