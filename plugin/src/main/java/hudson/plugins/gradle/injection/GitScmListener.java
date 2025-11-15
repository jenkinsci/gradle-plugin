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
import java.util.Set;

import static hudson.plugins.gradle.injection.InitScriptVariables.DEVELOCITY_INJECTION_ENABLED;
import static hudson.plugins.gradle.injection.MavenExtClasspathUtils.isUnix;
import static hudson.plugins.gradle.injection.MavenExtensionsDetector.detect;
import static hudson.plugins.gradle.injection.MavenInjectionAware.JENKINSGRADLEPLUGIN_MAVEN_PLUGIN_CONFIG_EXT_CLASSPATH;
import static hudson.plugins.gradle.injection.MavenInjectionAware.MAVEN_OPTS_HANDLER;
import static hudson.plugins.gradle.injection.MavenOptsHandler.MAVEN_OPTS;
import static hudson.plugins.gradle.injection.npm.NpmBuildScanInjection.DEVELOCITY_INTERNAL_DISABLE_AGENT;

@SuppressWarnings("unused")
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

            // By default, auto-injection is enabled. If it's not disabled for a repository - don't disable it
            if (!isInjectionEnabledForRepository(config, scm)) {
                disableAutoInjection(build, workspace, config, listener);
                return;
            }

            // Check .mvn/extensions.xml for already applied Develocity extension for maven injection only
            disableMavenAutoInjectionIfAlreadyApplied(build, workspace, config, listener);

            // TODO: Figure out how to detect if npm injection is already configured for the project.
            // If the project already has npm Develocity agent configured,
            // we should set DEVELOCITY_URL depending on the config.isEnforceUrl() flag.
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

        if (InjectionStatus.GRADLE.isEnabled(config)) {
            build.addAction(GradleInjectionDisabledAction.INSTANCE);
        }

        if (InjectionStatus.MAVEN.isEnabled(config)) {
            String currentMavenOpts = envVars.get(MavenOptsHandler.MAVEN_OPTS);
            if (currentMavenOpts != null) {
                String mavenOpts = Strings.nullToEmpty(MAVEN_OPTS_HANDLER.removeIfNeeded(currentMavenOpts));

                build.addAction(new MavenInjectionDisabledAction(mavenOpts));
            }
        }

        if (InjectionStatus.NPM.isEnabled(config)) {
            build.addAction(NpmInjectionDisabledAction.INSTANCE);
        }
    }

    private static void disableMavenAutoInjectionIfAlreadyApplied(
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

        String currentMavenOpts = envVars.get(MavenOptsHandler.MAVEN_OPTS);
        if (currentMavenOpts != null) {
            Set<MavenExtension> knownExtensions = detect(config, workspace);
            if (!knownExtensions.isEmpty()) {
                MavenOptsDevelocityFilter mavenOptsFilter = new MavenOptsDevelocityFilter(knownExtensions, isUnix(computer));
                String filteredMavenOpts = mavenOptsFilter.filter(currentMavenOpts, config.isEnforceUrl());
                build.addAction(new MavenInjectionDisabledAction(filteredMavenOpts));
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
                    case EXCLUDED:
                        return false;
                    case INCLUDED:
                        return true;
                }
            }
        }

        return false;
    }

    private enum InjectionStatus {
        GRADLE {
            @Override
            String getAgentVersion(InjectionConfig config) {
                return config.getGradlePluginVersion();
            }
        },
        MAVEN {
            @Override
            String getAgentVersion(InjectionConfig config) {
                return config.getMavenExtensionVersion();
            }
        },
        NPM {
            @Override
            String getAgentVersion(InjectionConfig config) {
                return config.getNpmAgentVersion();
            }
        };

        public boolean isEnabled(InjectionConfig config) {
            return InjectionUtil.isValid(InjectionConfig.checkRequiredVersion(getAgentVersion(config)));
        }

        abstract String getAgentVersion(InjectionConfig config);
    }

    /**
     * Action that disables Gradle Plugin injection by setting a flag to be read by the init script.
     */
    public static final class GradleInjectionDisabledAction extends InvisibleAction implements EnvironmentContributingAction {

        public static final GradleInjectionDisabledAction INSTANCE = new GradleInjectionDisabledAction();

        private GradleInjectionDisabledAction() {
        }

        @Override
        public void buildEnvironment(@Nonnull Run<?, ?> run, @Nonnull EnvVars envVars) {
            envVars.put(DEVELOCITY_INJECTION_ENABLED.getEnvVar(), "false");
        }
    }

    /**
     * Action that disables Maven Extension injection by modifying corresponding environment variables.
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

    public static final class NpmInjectionDisabledAction extends InvisibleAction implements EnvironmentContributingAction {

        public static final NpmInjectionDisabledAction INSTANCE = new NpmInjectionDisabledAction();

        private NpmInjectionDisabledAction() {
        }

        @Override
        public void buildEnvironment(@Nonnull Run<?, ?> run, @Nonnull EnvVars envVars) {
            envVars.put(DEVELOCITY_INTERNAL_DISABLE_AGENT, "true");
        }
    }
}
