package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.model.Node;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class MavenOptsHandler {

    public static final String MAVEN_OPTS = "MAVEN_OPTS";

    private static final String SPACE = " ";

    private final Set<String> keys;
    private final Set<String> requiredKeys;

    public MavenOptsHandler(SystemProperty.Key... keys) {
        this.keys =
            Arrays.stream(keys)
                .map(k -> k.name)
                .collect(
                    Collectors.collectingAndThen(Collectors.toSet(), Collections::unmodifiableSet));
        this.requiredKeys =
            Arrays.stream(keys)
                .filter(k -> k.required)
                .map(k -> k.name)
                .collect(
                    Collectors.collectingAndThen(Collectors.toSet(), Collections::unmodifiableSet));
    }

    String merge(EnvVars envVars, List<SystemProperty> systemProperties) {
        String additionalProperties =
            systemProperties
                .stream()
                .map(SystemProperty::asString)
                .collect(Collectors.joining(SPACE));

        return filtered(envVars.get(MAVEN_OPTS))
                .map(current -> String.join(SPACE, current, additionalProperties))
                .orElse(additionalProperties);
    }

    // Keep this method for cleaning MAVEN_OPTS set with previous versions of the plugin
    // as we now set it in EnvironmentContributor
    void removeIfNeeded(Node node) throws IOException, InterruptedException {
        String currentMavenOpts = EnvUtil.getEnv(node, MAVEN_OPTS);
        if (currentMavenOpts == null || currentMavenOpts.isEmpty()) {
            return;
        }

        boolean hasAllRequiredKeys = requiredKeys.stream().allMatch(currentMavenOpts::contains);
        if (!hasAllRequiredKeys) {
            return;
        }

        String mavenOpts = filtered(currentMavenOpts).orElse(null);
        EnvUtil.setEnvVar(node, MAVEN_OPTS, mavenOpts);
    }

    private Optional<String> filtered(@Nullable String mavenOpts) throws RuntimeException {
        return Optional.ofNullable(mavenOpts)
            .map(this::filterMavenOpts);
    }

    /**
     * Splits {@code MAVEN_OPTS} at each space and then removes all key value pairs containing any of the keys
     * that were added by the auto-injection.
     */
    @Nullable
    private String filterMavenOpts(String mavenOpts) {
        String filtered =
            Arrays.stream(mavenOpts.split(SPACE))
                .filter(this::shouldBeKept)
                .collect(Collectors.joining(SPACE));

        if (filtered.isEmpty()) {
            return null;
        }

        return filtered;
    }

    private boolean shouldBeKept(String systemProperty) {
        return keys.stream().noneMatch(systemProperty::contains);
    }
}
