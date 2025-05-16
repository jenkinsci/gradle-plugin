package hudson.plugins.gradle.injection;

import hudson.plugins.gradle.BuildAgentError;

public interface BuildAgentErrorListener {

    void onBuildAgentError(BuildAgentError buildAgentError);
}
