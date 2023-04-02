package hudson.plugins.gradle.injection;

import hudson.EnvVars;
import hudson.model.Node;

public interface BuildScanInjection {

    void inject(Node node, EnvVars envGlobal, EnvVars envComputer);

}
