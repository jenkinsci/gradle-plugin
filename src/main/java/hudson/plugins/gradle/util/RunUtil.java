package hudson.plugins.gradle.util;

import hudson.model.Actionable;
import hudson.model.FreeStyleBuild;
import hudson.model.Run;
import hudson.plugins.gradle.BuildScanAction;
import hudson.plugins.gradle.Gradle;
import hudson.tasks.Builder;

import javax.annotation.Nullable;

public final class RunUtil {

    private RunUtil() {
    }

    public static BuildScanAction getOrCreateBuildScanAction(Actionable build) {
        BuildScanAction action = build.getAction(BuildScanAction.class);

        if (action == null) {
            action = new BuildScanAction();
            build.addAction(action);
        }

        return action;
    }

    public static boolean isFreestyleBuildWithGradle(@Nullable Run<?, ?> build) {
        if (build == null) {
            return false;
        }
        if (build instanceof FreeStyleBuild) {
            for (Builder builder : ((FreeStyleBuild) build).getProject().getBuildersList()) {
                if (builder instanceof Gradle) {
                    return true;
                }
            }
        }
        return false;
    }
}
