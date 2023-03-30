package hudson.plugins.gradle.injection;

import hudson.Extension;
import hudson.model.FreeStyleBuild;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

@Extension
public class MavenFreeStyleRunListener extends RunListener<FreeStyleBuild> implements MavenInjectionAware {

    @Override
    public void onStarted(FreeStyleBuild freeStyleBuild, TaskListener listener) {
        super.onStarted(freeStyleBuild, listener);

        setPreparedMavenProperties(freeStyleBuild, listener);
    }

}
