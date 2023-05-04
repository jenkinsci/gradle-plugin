package hudson.plugins.gradle;

import hudson.model.Actionable;
import hudson.plugins.gradle.enriched.ScanDetailService;
import hudson.plugins.gradle.util.RunUtil;

public class DefaultBuildScanPublishedListener implements BuildScanPublishedListener {

    private final Actionable target;
    private final ScanDetailService scanDetailService;

    DefaultBuildScanPublishedListener(Actionable target, ScanDetailService scanDetailService) {
        this.target = target;
        this.scanDetailService = scanDetailService;
    }

    @Override
    public void onBuildScanPublished(String scanUrl) {
        BuildScanAction action = RunUtil.getOrCreateBuildScanAction(target);

        action.addScanUrl(scanUrl);
        scanDetailService.getScanDetail(scanUrl)
            .ifPresent(action::addScanDetail);
    }

}
