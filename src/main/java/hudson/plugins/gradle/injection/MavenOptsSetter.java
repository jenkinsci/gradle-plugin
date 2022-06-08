package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.model.Node;
import hudson.slaves.EnvironmentVariablesNodeProperty;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

class MavenOptsSetter {

    private static final String MAVEN_OPTS_VAR = "MAVEN_OPTS";
    private final Set<String> keys;

    public MavenOptsSetter(String... keys) {
        this.keys = new HashSet<>(Arrays.asList(keys));
    }

    void appendIfMissing(Node node, List<String> mavenOptsKeyValuePairs) throws IOException, InterruptedException {
        String mavenOpts = removeSystemProperties(getMavenOpts(node)) + " " + String.join(" ", mavenOptsKeyValuePairs);
        setMavenOpts(node, mavenOpts);
    }

    void remove(Node node) throws IOException, InterruptedException {
        String mavenOpts = removeSystemProperties(getMavenOpts(node));
        setMavenOpts(node, mavenOpts);
    }

    private String getMavenOpts(Node node) throws IOException, InterruptedException {
        EnvVars nodeEnvVars = EnvVars.getRemote(node.getChannel());
        return nodeEnvVars.get(MAVEN_OPTS_VAR);
    }

    private void setMavenOpts(Node node, String mavenOpts) {
        node.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry(MAVEN_OPTS_VAR, mavenOpts)));
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
        return Arrays.stream(mavenOpts.split(" "))
                .filter(this::shouldBeKept)
                .collect(Collectors.joining(" "))
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
