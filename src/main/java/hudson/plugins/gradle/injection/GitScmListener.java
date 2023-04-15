package hudson.plugins.gradle.injection;

import com.google.common.base.Strings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
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
import java.io.File;
import java.util.List;

import static hudson.plugins.gradle.injection.MavenInjectionAware.MAVEN_OPTS_HANDLER;

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

            disabledAutoInjection(build, workspace, config, listener);
        } catch (Exception e) {
            LOGGER.error("Error occurred when processing onCheckout notification", e);
        }
    }

    private static void disabledAutoInjection(Run<?, ?> build,
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

                build.addAction(new MavenInjectionDisabledMavenOptsAction(mavenOpts));
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

                if (config.isRepositoryExcluded(url)) {
                    return false;
                }

                if (config.isRepositoryIncluded(url)) {
                    return true;
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
     * Action that holds Maven environment variables to be set in {@link MavenInjectionEnvironmentContributor}.
     */
    public static final class MavenInjectionDisabledMavenOptsAction extends InvisibleAction {

        public final String mavenOpts;

        public MavenInjectionDisabledMavenOptsAction(String mavenOpts) {
            this.mavenOpts = mavenOpts;
        }

    }

    /**
     * Marker action to ensure we disable injection in {@link GradleInjectionEnvironmentContributor}.
     */
    public static final class GradleInjectionDisabledAction extends InvisibleAction {

        public static final GradleInjectionDisabledAction INSTANCE = new GradleInjectionDisabledAction();

        private GradleInjectionDisabledAction() {
        }

    }

}
