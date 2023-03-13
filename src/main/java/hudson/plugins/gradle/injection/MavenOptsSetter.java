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

class MavenOptsSetter {

    private static final String SPACE = " ";
    private static final String MAVEN_OPTS_VAR = "MAVEN_OPTS";

    private final Set<String> keys;
    private final Set<String> requiredKeys;

    public MavenOptsSetter(SystemProperty.Key... keys) {
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

    void append(Node node, List<SystemProperty> systemProperties) throws IOException, InterruptedException {
        String additionalProperties =
            systemProperties
                .stream()
                .map(SystemProperty::asString)
                .collect(Collectors.joining(SPACE));

        String mavenOpts =
            filtered(getCurrentMavenOpts(node))
                .map(current -> String.join(SPACE, current, additionalProperties))
                .orElse(additionalProperties);

        EnvUtil.setEnvVar(node, MAVEN_OPTS_VAR, mavenOpts);
    }

    void removeIfNeeded(Node node) throws IOException, InterruptedException {
        String currentMavenOpts = getCurrentMavenOpts(node);
        if (currentMavenOpts == null || currentMavenOpts.isEmpty()) {
            return;
        }

        boolean hasAllRequiredKeys = requiredKeys.stream().allMatch(currentMavenOpts::contains);
        if (!hasAllRequiredKeys) {
            return;
        }

        String mavenOpts = filtered(currentMavenOpts).orElse(null);
        EnvUtil.setEnvVar(node, MAVEN_OPTS_VAR, mavenOpts);
    }

    @Nullable
    private static String getCurrentMavenOpts(Node node) {
        return EnvUtil.getEnv(node, MAVEN_OPTS_VAR);
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
