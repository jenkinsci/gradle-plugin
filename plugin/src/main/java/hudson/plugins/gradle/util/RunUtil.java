package hudson.plugins.gradle.util;

import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.FreeStyleBuild;
import hudson.model.Run;
import hudson.plugins.gradle.Gradle;
import hudson.tasks.Builder;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public final class RunUtil {

    private RunUtil() {
    }

    public static <T extends Actionable, A extends Action> A getOrCreateAction(
        T actionable,
        Class<A> actionClass,
        Supplier<A> actionFactory
    ) {
        A action = actionable.getAction(actionClass);

        if (action == null) {
            action = actionFactory.get();
            actionable.addAction(action);
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
