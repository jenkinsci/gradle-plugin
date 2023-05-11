package hudson.plugins.gradle.injection;

import com.google.common.base.Strings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.EnvironmentContributingAction;
import hudson.model.InvisibleAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.util.List;

import static hudson.plugins.gradle.injection.GradleInjectionAware.JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_GRADLE_INJECTION_ENABLED;
import static hudson.plugins.gradle.injection.MavenInjectionAware.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH;
import static hudson.plugins.gradle.injection.MavenInjectionAware.MAVEN_OPTS_HANDLER;
import static hudson.plugins.gradle.injection.MavenOptsHandler.MAVEN_OPTS;

@Extension
public class GitScmListener extends SCMListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitScmListener.class);

    @Override
    public void onCheckout(
        Run<?, ?> build,
        SCM scm,
        FilePath workspace,
        TaskListener listener,
        @CheckForNull File changelogFile,
        @CheckForNull SCMRevisionState pollingBaseline
    ) {
        try {
            InjectionConfig config = InjectionConfig.get();

            if (isInjectionGloballyDisabled(config)) {
                return;
            }

            // By default, auto-injection is enabled. If repository matches the VCS filter we don't need to disable it
            if (isInjectionEnabledForRepository(config, scm)) {
                return;
            }

            disableAutoInjection(build, workspace, config, listener);
        } catch (Exception e) {
            LOGGER.error("Error occurred when processing onCheckout notification", e);
        }
    }

    private static void disableAutoInjection(
            Run<?, ?> build,
            FilePath workspace,
            InjectionConfig config,
            TaskListener listener
    ) throws Exception {
        Computer computer = workspace.toComputer();
        if (computer == null) {
            return;
        }

        EnvVars envVars = computer.buildEnvironment(listener);

        if (shouldDisableGradleInjection(config)) {
            build.addAction(GradleInjectionDisabledAction.INSTANCE);
        }

        if (shouldDisableMavenInjection(config)) {
            String currentMavenOpts = envVars.get(MavenOptsHandler.MAVEN_OPTS);
            if (currentMavenOpts != null) {
                String mavenOpts = Strings.nullToEmpty(MAVEN_OPTS_HANDLER.removeIfNeeded(currentMavenOpts));

                build.addAction(new MavenInjectionDisabledAction(mavenOpts));
            }
        }
    }

    private static boolean isInjectionGloballyDisabled(InjectionConfig config) {
        return config.isDisabled() || InjectionUtil.isInvalid(InjectionConfig.checkRequiredUrl(config.getServer()));
    }

    private static boolean isInjectionEnabledForRepository(InjectionConfig config, SCM scm) {
        if (!config.hasRepositoryFilter()) {
            return true;
        }

        if (scm instanceof GitSCM) {
            List<UserRemoteConfig> userRemoteConfigs = ((GitSCM) scm).getUserRemoteConfigs();

            for (UserRemoteConfig userRemoteConfig : userRemoteConfigs) {
                String url = userRemoteConfig.getUrl();
                if (url == null) {
                    return true;
                }
                switch (config.matchesRepositoryFilter(url)) {
                    case EXCLUDED: return false;
                    case INCLUDED: return true;
                }
            }
        }

        return false;
    }

    private static boolean shouldDisableGradleInjection(InjectionConfig config) {
        return InjectionUtil.isValid(InjectionConfig.checkRequiredVersion(config.getGradlePluginVersion()));
    }

    private static boolean shouldDisableMavenInjection(InjectionConfig config) {
        return config.isInjectMavenExtension();
    }

    /**
     * Action that disables Gradle Plugin Injection by setting a flag to be read via init script.
     */
    public static final class GradleInjectionDisabledAction extends InvisibleAction implements EnvironmentContributingAction {

        public static final GradleInjectionDisabledAction INSTANCE = new GradleInjectionDisabledAction();

        private GradleInjectionDisabledAction() {
        }

        @Override
        public void buildEnvironment(@Nonnull Run<?, ?> run, @Nonnull EnvVars envVars) {
            envVars.put(JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_GRADLE_INJECTION_ENABLED, "false");
        }

    }

    /**
     * Action that disables Maven Extension Injection by modifying corresponding environment variables.
     */
    public static final class MavenInjectionDisabledAction extends InvisibleAction implements EnvironmentContributingAction {

        private final String mavenOpts;

        public MavenInjectionDisabledAction(String mavenOpts) {
            this.mavenOpts = mavenOpts;
        }

        @Override
        public void buildEnvironment(@Nonnull Run<?, ?> run, @Nonnull EnvVars envVars) {
            envVars.put(MAVEN_OPTS, mavenOpts);
            envVars.put(JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH, "");
        }

    }

}
