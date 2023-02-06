package hudson.plugins.gradle;

import hudson.model.Actionable;
import hudson.plugins.gradle.enriched.ScanDetail;
import hudson.plugins.gradle.enriched.ScanDetailService;

import java.util.Optional;

public class DefaultBuildScanPublishedListener implements BuildScanPublishedListener {

    private final Actionable target;

    private final ScanDetailService scanDetailService;

    DefaultBuildScanPublishedListener(Actionable target, ScanDetailService scanDetailService) {
        this.target = target;
        this.scanDetailService = scanDetailService;
    }

    @Override
    public void onBuildScanPublished(String scanUrl) {
        BuildScanAction action = target.getAction(BuildScanAction.class);

        if (action == null) {
            action = new BuildScanAction();
            target.addAction(action);
        }

        action.addScanUrl(scanUrl);
        processScanDetail(action, scanUrl);
    }

    private void processScanDetail(BuildScanAction action, String scanUrl) {
        Optional<ScanDetail> scanDetail = scanDetailService.getScanDetail(scanUrl);
        scanDetail.ifPresent(action::addScanDetail);
    }

}
