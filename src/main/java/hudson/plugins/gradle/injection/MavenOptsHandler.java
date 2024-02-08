package hudson.plugins.gradle.injection;

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

    String merge(Node node, List<SystemProperty> systemProperties) {
        String additionalProperties =
            systemProperties
                .stream()
                .map(SystemProperty::asString)
                .collect(Collectors.joining(SPACE));

        return filtered(getCurrentMavenOpts(node), Collections.emptySet())
                .map(current -> String.join(SPACE, current, additionalProperties))
                .orElse(additionalProperties);
    }

    void removeIfNeeded(Node node) throws IOException, InterruptedException {
        String currentMavenOpts = EnvUtil.getEnv(node, MAVEN_OPTS);
        if (currentMavenOpts == null || currentMavenOpts.isEmpty()) {
            return;
        }

        boolean hasAllRequiredKeys = requiredKeys.stream().allMatch(currentMavenOpts::contains);
        if (!hasAllRequiredKeys) {
            return;
        }

        String mavenOpts = filtered(currentMavenOpts, Collections.emptySet()).orElse(null);
        EnvUtil.setEnvVar(node, MAVEN_OPTS, mavenOpts);
    }

    public String removeIfNeeded(String currentMavenOpts) {
        return removeIfNeeded(currentMavenOpts, Collections.emptySet());
    }

    public String removeIfNeeded(String currentMavenOpts, Set<String> keepKeys) {
        if (currentMavenOpts == null || currentMavenOpts.isEmpty()) {
            return null;
        }

        boolean hasAllRequiredKeys = requiredKeys.stream().allMatch(currentMavenOpts::contains);
        if (!hasAllRequiredKeys) {
            return null;
        }

        return filtered(currentMavenOpts, keepKeys).orElse(null);
    }

    private Optional<String> filtered(@Nullable String mavenOpts, Set<String> keepKeys) throws RuntimeException {
        return Optional.ofNullable(mavenOpts)
            .map(it -> filterMavenOpts(it, keepKeys));
    }

    @Nullable
    private static String getCurrentMavenOpts(Node node) {
        return EnvUtil.getEnv(node, MAVEN_OPTS);
    }

    /**
     * Splits {@code MAVEN_OPTS} at each space and then removes all key value pairs containing any of the keys
     * that were added by the auto-injection.
     */
    @Nullable
    private String filterMavenOpts(String mavenOpts, Set<String> keepKeys) {
        String filtered =
            Arrays.stream(mavenOpts.split(SPACE))
                .filter(systemProperty -> shouldBeKept(systemProperty, keepKeys))
                .collect(Collectors.joining(SPACE));

        if (filtered.isEmpty()) {
            return null;
        }

        return filtered;
    }

    private boolean shouldBeKept(String systemProperty, Set<String> keepKeys) {
        return keys.stream().filter(it -> !keepKeys.contains(it)).noneMatch(systemProperty::contains);
    }
}
