package hudson.plugins.gradle.injection;

import hudson.Extension;
import hudson.maven.AbstractMavenBuild;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

@Extension
public class MavenAbstractBuildRunListener extends RunListener<AbstractMavenBuild<?, ?>> implements MavenInjectionAware {

    @Override
    public void onStarted(AbstractMavenBuild abstractMavenBuild, TaskListener listener) {
        super.onInitialize(abstractMavenBuild);

        setPreparedMavenProperties(abstractMavenBuild, listener);
    }

}
