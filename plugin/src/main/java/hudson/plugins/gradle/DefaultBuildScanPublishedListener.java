package hudson.plugins.gradle;

import hudson.model.Actionable;
import hudson.plugins.gradle.enriched.ScanDetailService;
import hudson.plugins.gradle.util.RunUtil;
import java.util.Collections;

public class DefaultBuildScanPublishedListener implements BuildScanPublishedListener {

    private final Actionable target;
    private final ScanDetailService scanDetailService;

    DefaultBuildScanPublishedListener(Actionable target, ScanDetailService scanDetailService) {
        this.target = target;
        this.scanDetailService = scanDetailService;
    }

    @Override
    public void onBuildScanPublished(String scanUrl) {
        RunUtil.getOrCreateAction(target, BuildScanAction.class, BuildScanAction::new)
                .addScanUrls(Collections.singleton(scanUrl), scanDetailService::getScanDetail);
    }
}
