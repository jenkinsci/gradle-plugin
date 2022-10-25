package hudson.plugins.gradle.injection;

import com.google.common.collect.ImmutableSet;
import hudson.EnvVars;
import hudson.model.Node;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

class MavenOptsSetter {

    private static final String SPACE = " ";
    private static final String MAVEN_OPTS_VAR = "MAVEN_OPTS";

    private final Set<String> keys;

    public MavenOptsSetter(String... keys) {
        this.keys = ImmutableSet.copyOf(keys);
    }

    void appendIfMissing(Node node, List<SystemProperty> systemProperties) throws IOException, InterruptedException {
        String geMavenOpts =
            systemProperties
                .stream()
                .map(SystemProperty::asString)
                .collect(Collectors.joining(SPACE));

        String mavenOpts = removeSystemProperties(getMavenOpts(node)) + SPACE + geMavenOpts;
        EnvUtil.setEnvVar(node, MAVEN_OPTS_VAR, mavenOpts);
    }

    void remove(Node node) throws IOException, InterruptedException {
        String mavenOpts = removeSystemProperties(getMavenOpts(node));
        EnvUtil.setEnvVar(node, MAVEN_OPTS_VAR, mavenOpts);
    }

    private String getMavenOpts(Node node) throws IOException, InterruptedException {
        EnvVars nodeEnvVars = EnvVars.getRemote(node.getChannel());
        return nodeEnvVars.get(MAVEN_OPTS_VAR);
    }

    private String removeSystemProperties(String mavenOpts) throws RuntimeException {
        return Optional.ofNullable(mavenOpts)
            .map(this::filterMavenOpts)
            .orElse("");
    }

    /**
     * Splits MAVEN_OPTS at each space and then removes all key value pairs that contain
     * any of the keys we want to remove.
     */
    private String filterMavenOpts(String mavenOpts) {
        return Arrays.stream(mavenOpts.split(SPACE))
            .filter(this::shouldBeKept)
            .collect(Collectors.joining(SPACE))
            .trim();
    }

    /**
     * Checks for a MAVEN_OPTS key value pair whether it contains none of the keys we're looking for.
     * In other words if this segment none of the keys, this method returns true.
     */
    private boolean shouldBeKept(String seg) {
        return keys.stream().noneMatch(seg::contains);
    }
}
