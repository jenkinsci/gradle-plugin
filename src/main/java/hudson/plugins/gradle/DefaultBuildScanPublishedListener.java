package hudson.plugins.gradle;

import hudson.model.Actionable;
import hudson.plugins.gradle.enriched.ScanDetail;
import hudson.plugins.gradle.enriched.ScanDetailService;
import hudson.plugins.gradle.enriched.ScanDetailServiceDefaultImpl;
import hudson.util.Secret;

import java.net.URI;

public class DefaultBuildScanPublishedListener implements BuildScanPublishedListener {

    private final Actionable target;

    private final ScanDetailService scanDetailService;

    DefaultBuildScanPublishedListener(Actionable target, Secret buildScanAccessToken, URI buildScanServer) {
        this.target = target;
        this.scanDetailService = new ScanDetailServiceDefaultImpl(buildScanAccessToken, buildScanServer);
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

    void processScanDetail(BuildScanAction action, String scanUrl) {
        ScanDetail scanDetail = scanDetailService.getScanDetail(scanUrl);
        if (scanDetail != null) {
            action.addScanDetail(scanDetail);
        }
    }

}
