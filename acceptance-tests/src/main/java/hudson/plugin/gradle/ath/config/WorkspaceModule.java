package hudson.plugin.gradle.ath.config;

import com.cloudbees.sdk.extensibility.Extension;
import com.cloudbees.sdk.extensibility.ExtensionModule;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import java.io.File;

/**
 * @see org.jenkinsci.test.acceptance.FallbackConfig#getWorkspace
 */
@Extension
public class WorkspaceModule extends AbstractModule implements ExtensionModule {

    @Override
    protected void configure() {
        bind(String.class)
            .annotatedWith(Names.named("WORKSPACE"))
            .toInstance(new File(System.getProperty("user.dir"), "build").getPath());
    }
}
