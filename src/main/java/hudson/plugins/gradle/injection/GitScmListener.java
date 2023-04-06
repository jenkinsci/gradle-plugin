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

            if (isVCSInjectionFilteringNotApplied(scm, config)) {
                return;
            }

            Computer computer = workspace.toComputer();
            if (computer != null) {
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
        } catch (Exception e) {
            LOGGER.error("Error occurred when processing onCheckout notification", e);
        }
    }

    private static boolean isVCSInjectionFilteringNotApplied(SCM scm, InjectionConfig config) {
        return config.isDisabled()
                || InjectionUtil.isInvalid(InjectionConfig.checkRequiredUrl(config.getServer()))
                || config.getParsedInjectionVcsRepositoryPatterns() == null
                || config.getParsedInjectionVcsRepositoryPatterns().isEmpty()
                || vcsRepositoryUrlMatches(config, scm);
    }

    private static boolean vcsRepositoryUrlMatches(InjectionConfig config, SCM scm) {
        if (scm instanceof GitSCM) {
            List<UserRemoteConfig> userRemoteConfigs = ((GitSCM) scm).getUserRemoteConfigs();

            for (UserRemoteConfig userRemoteConfig : userRemoteConfigs) {
                for (String pattern : config.getParsedInjectionVcsRepositoryPatterns()) {
                    String url = userRemoteConfig.getUrl();
                    if (url != null && url.contains(pattern)) {
                        return true;
                    }
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
    public static class MavenInjectionDisabledMavenOptsAction extends InvisibleAction {

        public final String mavenOpts;

        public MavenInjectionDisabledMavenOptsAction(String mavenOpts) {
            this.mavenOpts = mavenOpts;
        }

    }

    /**
     * Marker action to ensure we disable injection in {@link GradleInjectionEnvironmentContributor}.
     */
    public static class GradleInjectionDisabledAction extends InvisibleAction {

        public static final GradleInjectionDisabledAction INSTANCE = new GradleInjectionDisabledAction();

        private GradleInjectionDisabledAction() {
        }

    }

}
