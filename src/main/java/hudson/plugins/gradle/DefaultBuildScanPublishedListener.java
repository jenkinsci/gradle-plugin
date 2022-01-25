package hudson.plugins.gradle;

import hudson.model.Actionable;

public class DefaultBuildScanPublishedListener implements BuildScanPublishedListener {
    private final Actionable target;
    private final String scanLabel;

    DefaultBuildScanPublishedListener(Actionable target) {
        this(target, null);
    }

    DefaultBuildScanPublishedListener(Actionable target, String scanLabel) {
        this.target = target;
        this.scanLabel = scanLabel;
    }

    @Override
    public void onBuildScanPublished(String scanUrl) {
        BuildScanAction action = target.getAction(BuildScanAction.class);

        if (action == null) {
            action = new BuildScanAction();
            action.addScanUrl(scanUrl, scanLabel);

            target.addAction(action);
        } else {
            action.addScanUrl(scanUrl, scanLabel);
        }
    }
}
