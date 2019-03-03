package hudson.plugins.gradle;

import hudson.model.Actionable;

public class DefaultBuildScanPublishedListener implements BuildScanPublishedListener {
    private final Actionable target;

    DefaultBuildScanPublishedListener(Actionable target) {
        this.target = target;
    }

    @Override
    public void onBuildScanPublished(String scanUrl) {
        BuildScanAction action = target.getAction(BuildScanAction.class);

        if (action == null) {
            action = new BuildScanAction();
            action.addScanUrl(scanUrl);

            target.addAction(action);
        } else {
            action.addScanUrl(scanUrl);
        }
    }
}
