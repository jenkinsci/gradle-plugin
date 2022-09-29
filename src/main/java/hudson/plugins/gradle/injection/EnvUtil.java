package hudson.plugins.gradle.injection;

import com.google.common.collect.Iterables;
import hudson.EnvVars;
import hudson.model.Node;
import hudson.slaves.EnvironmentVariablesNodeProperty;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

// TODO: Move to util package
public final class EnvUtil {

    private EnvUtil() {
    }

    public static String getEnv(EnvVars env, String key) {
        return env != null ? env.get(key) : null;
    }

    public static void removeEnvVar(Node node, String key) {
        setEnvVar(node, key, null);
    }

    public static void setEnvVar(Node node, String key, @Nullable String value) {
        List<EnvironmentVariablesNodeProperty> all =
            node.getNodeProperties().getAll(EnvironmentVariablesNodeProperty.class);

        if (all.isEmpty()) {
            if (value != null) {
                node.getNodeProperties().add(
                    new EnvironmentVariablesNodeProperty(
                        new EnvironmentVariablesNodeProperty.Entry(key, value)));
            } // noop if null
            return;
        }

        EnvironmentVariablesNodeProperty last = Iterables.getLast(all);
        if (!Objects.equals(value, last.getEnvVars().get(key))) {
            if (value != null) {
                last.getEnvVars().put(key, value);
            } else {
                last.getEnvVars().remove(key);
            }
        }
    }
}
