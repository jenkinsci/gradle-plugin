package hudson.plugins.gradle;

import hudson.model.Actionable;
import hudson.plugins.gradle.enriched.ScanDetail;
import hudson.plugins.gradle.enriched.ScanDetailService;
import hudson.plugins.gradle.enriched.ScanDetailServiceDefaultImpl;
import hudson.util.Secret;

public class DefaultBuildScanPublishedListener implements BuildScanPublishedListener {

    private final Actionable target;

    private final ScanDetailService scanDetailService;

    DefaultBuildScanPublishedListener(Actionable target, Secret buildScanAccessToken) {
        this.target = target;
        this.scanDetailService = new ScanDetailServiceDefaultImpl(buildScanAccessToken);
    }

    @Override
    public void onBuildScanPublished(String scanUrl) {
        BuildScanAction action = target.getAction(BuildScanAction.class);

        if (action == null) {
            action = new BuildScanAction();
            action.addScanUrl(scanUrl);

            processScanDetail(action, scanUrl);

            target.addAction(action);
        } else {
            action.addScanUrl(scanUrl);

            processScanDetail(action, scanUrl);
        }
    }

    void processScanDetail(BuildScanAction action, String scanUrl) {
        ScanDetail scanDetail = scanDetailService.getScanDetail(scanUrl);
        if (scanDetail != null) {
            action.addScanDetail(scanDetail);
        }
    }

}
