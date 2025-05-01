package hudson.plugins.gradle.injection;

import hudson.model.Actionable;
import hudson.plugins.gradle.BuildAgentError;
import hudson.plugins.gradle.BuildScanAction;
import hudson.plugins.gradle.util.RunUtil;

public final class DefaultBuildAgentErrorListener implements BuildAgentErrorListener {

    private final Actionable actionable;

    public DefaultBuildAgentErrorListener(Actionable actionable) {
        this.actionable = actionable;
    }

    @Override
    public void onBuildAgentError(BuildAgentError buildAgentError) {
        RunUtil.getOrCreateAction(actionable, BuildScanAction.class, BuildScanAction::new)
                .addBuildAgentError(buildAgentError);
    }
}
