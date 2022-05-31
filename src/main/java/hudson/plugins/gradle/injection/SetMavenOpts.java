package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import jenkins.security.MasterToSlaveCallable;

import java.util.List;
import java.util.logging.Logger;

public class SetMavenOpts extends MasterToSlaveCallable<Void, RuntimeException> {

    private static final Logger LOGGER = Logger.getLogger(SetMavenOpts.class.getName());

    private final String mavenOptsValue;
    public SetMavenOpts(List<String> mavenOptsKeyValuePairs) {
        this.mavenOptsValue = String.join(" ", mavenOptsKeyValuePairs);
    }

    @Override
    public Void call() {
        String newMavenOpts;
        String oldMavenOpts = EnvVars.masterEnvVars.get("MAVEN_OPTS");
        if (oldMavenOpts != null) {
            newMavenOpts = oldMavenOpts + " " + mavenOptsValue;
        } else {
            newMavenOpts = mavenOptsValue;
        }
        EnvVars.masterEnvVars.put("MAVEN_OPTS", newMavenOpts);
        return null;
    }
}
