package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.model.Node;

public interface BuildScanInjection {

    boolean isEnabled(Node node);

    void inject(Node node, EnvVars envGlobal, EnvVars envComputer);
}
