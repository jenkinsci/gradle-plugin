package hudson.plugins.gradle;

import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tools.ToolInstallation;
import hudson.util.ArgumentListBuilder;
import net.sf.json.JSONObject;
import org.jenkinsci.lib.dryrun.DryRun;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * @author Gregory Boissinot
 */
public class Gradle extends Builder implements DryRun {

    private final String description;
    private final String switches;
    private final String tasks;
    private final String rootBuildScriptDir;
    private final String buildFile;
    private final String gradleName;
    private final boolean useWrapper;
    private final boolean useLauncherJar;
    private final boolean makeExecutable;
    private final boolean fromRootBuildScriptDir;
    private final String launcherJar;
    private final boolean useWorkspaceAsHome;

    @DataBoundConstructor
    public Gradle(String description, String switches, String tasks, String rootBuildScriptDir, String buildFile,
                  String gradleName, boolean useWrapper, boolean useLauncherJar, String launcherJar, boolean makeExecutable, boolean fromRootBuildScriptDir, boolean useWorkspaceAsHome) {
        this.description = description;
        this.switches = switches;
        this.tasks = tasks;
        this.gradleName = gradleName;
        this.rootBuildScriptDir = rootBuildScriptDir;
        this.buildFile = buildFile;
        this.useWrapper = useWrapper;
        this.useLauncherJar = useLauncherJar;
        this.launcherJar = launcherJar;
        this.makeExecutable = makeExecutable;
        this.fromRootBuildScriptDir = fromRootBuildScriptDir;
        this.useWorkspaceAsHome = useWorkspaceAsHome;
    }

    @SuppressWarnings("unused")
    public String getSwitches() {
        return switches;
    }

    @SuppressWarnings("unused")
    public String getBuildFile() {
        return buildFile;
    }

    @SuppressWarnings("unused")
    public String getGradleName() {
        return gradleName;
    }

    @SuppressWarnings("unused")
    public String getTasks() {
        return tasks;
    }

    @SuppressWarnings("unused")
    public String getDescription() {
        return description;
    }

    @SuppressWarnings("unused")
    public boolean isUseWrapper() {
        return useWrapper;
    }

    @SuppressWarnings("unused")
    public boolean isUseLauncherJar() {
        return useLauncherJar;
    }

    @SuppressWarnings("unused")
    public String getLauncherJar() {
        return launcherJar;
    }

    @SuppressWarnings("unused")
    public String getRootBuildScriptDir() {
        return rootBuildScriptDir;
    }

    @SuppressWarnings("unused")
    public boolean isMakeExecutable() {
        return makeExecutable;
    }

    @SuppressWarnings("unused")
    public boolean isFromRootBuildScriptDir() {
        return fromRootBuildScriptDir;
    }

    @SuppressWarnings("unused")
    public boolean isUseWorkspaceAsHome() {
        return useWorkspaceAsHome;
    }

    public GradleInstallation getGradle() {
        for (GradleInstallation i : getDescriptor().getInstallations()) {
            if (gradleName != null && i.getName().equals(gradleName)) {
                return i;
            }
        }
        return null;
    }

    @Override
    public boolean performDryRun(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        return performTask(true, build, launcher, listener);
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        return performTask(false, build, launcher, listener);
    }

    private boolean performTask(boolean dryRun, AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {

        GradleLogger gradleLogger = new GradleLogger(listener);
        gradleLogger.info("Launching build.");

        EnvVars env = build.getEnvironment(listener);

        //Switches
        String extraSwitches = env.get("GRADLE_EXT_SWITCHES");
        String normalizedSwitches;
        if (extraSwitches != null) {
            normalizedSwitches = switches + " " + extraSwitches;
        } else {
            normalizedSwitches = switches;
        }
        normalizedSwitches = normalizedSwitches.replaceAll("[\t\r\n]+", " ");
        normalizedSwitches = Util.replaceMacro(normalizedSwitches, env);
        normalizedSwitches = Util.replaceMacro(normalizedSwitches, build.getBuildVariables());

        //Add dry-run switch if needed
        if (dryRun) {
            normalizedSwitches = normalizedSwitches + " --dry-run";
        }

        //Tasks
        String extraTasks = env.get("GRADLE_EXT_TASKS");
        String normalizedTasks;
        if (extraTasks != null) {
            normalizedTasks = tasks + " " + extraTasks;
        } else {
            normalizedTasks = tasks;
        }
        normalizedTasks = normalizeString(build, env, normalizedTasks);

        FilePath normalizedRootBuildScriptDir = normalizePath(build, env, rootBuildScriptDir);
        FilePath normalizedLauncherJar = normalizePath(build, env, launcherJar);

        //Build arguments
        GradleInstallation ai = getGradle();
        if (ai != null) {
            ai = ai.forNode(getCurrentNode(), listener).forEnvironment(env);
        }
        ArgumentListBuilder args = new ArgumentListBuilder();
        if (useLauncherJar) {
            if (normalizedLauncherJar == null) {
                if (useWrapper) {
                    String gradleLauncherJar = "gradle/wrapper/gradle-wrapper.jar";
                    normalizedLauncherJar = ((fromRootBuildScriptDir && (normalizedRootBuildScriptDir != null))
                        ? new FilePath(normalizedRootBuildScriptDir, gradleLauncherJar)
                        : new FilePath(build.getModuleRoot(), gradleLauncherJar));
                } else if (ai != null) {
                    String str = ai.getLauncher(launcher);
                    if (str != null) {
                        normalizedLauncherJar = new FilePath(launcher.getChannel(), str);
                    }
                }
            }
            if (normalizedLauncherJar == null) {
                gradleLogger.error("Can't retrieve the Gradle launcher/wrapper jar.");
                return false;
            }
            String className = normalizedLauncherJar.getName().contains("gradle-wrapper")
                ? "org.gradle.wrapper.GradleWrapperMain"
                : "org.gradle.launcher.GradleMain";

            JDK jdk = build.getProject().getJDK();
            if (jdk != null) {
                jdk = jdk.forNode(getCurrentNode(), listener).forEnvironment(env);
            }
            if (jdk == null) {
                args.add("java");
            } else {
                args.add(new File(jdk.getBinDir(), "java").getPath());
            }
            args.add("-cp").add(normalizedLauncherJar.getRemote()).add(className);
        }
        else if (useWrapper) {
            String execName = (launcher.isUnix()) ? GradleInstallation.UNIX_GRADLE_WRAPPER_COMMAND : GradleInstallation.WINDOWS_GRADLE_WRAPPER_COMMAND;
            FilePath gradleWrapperFile = ((fromRootBuildScriptDir && (normalizedRootBuildScriptDir != null))
                                          ? new FilePath(normalizedRootBuildScriptDir, execName)
                                          : new FilePath(build.getModuleRoot(), execName));
            if (makeExecutable) {
                gradleWrapperFile.chmod(0744);
            }
            args.add(gradleWrapperFile.getRemote());
        } else if (ai != null) {
            String exe = ai.getExecutable(launcher);
            if (exe == null) {
                gradleLogger.error("Can't retrieve the Gradle executable.");
                return false;
            }
            args.add(exe);
        } else {
            args.add(launcher.isUnix() ? GradleInstallation.UNIX_GRADLE_COMMAND : GradleInstallation.WINDOWS_GRADLE_COMMAND);
        }
        Set<String> sensitiveVars = build.getSensitiveBuildVariables();
        args.addKeyValuePairs("-D", build.getBuildVariables(), sensitiveVars);
        args.addTokenized(normalizedSwitches);
        args.addTokenized(normalizedTasks);
        if (buildFile != null && buildFile.trim().length() != 0) {
            String buildFileNormalized = Util.replaceMacro(buildFile.trim(), env);
            args.add("-b");
            args.add(buildFileNormalized);
        }
        if (ai != null) {
            env.put("GRADLE_HOME", ai.getHome());
        }

        if (useWorkspaceAsHome) {
            // Make user home relative to the workspace, so that files aren't shared between builds
            env.put("GRADLE_USER_HOME", build.getWorkspace().getRemote());
        }

        if (!launcher.isUnix() && !useLauncherJar) {
            args = args.toWindowsCommand();
        }

        FilePath rootLauncher;
        if (normalizedRootBuildScriptDir != null) {
            rootLauncher = normalizedRootBuildScriptDir;
        } else {
            rootLauncher = build.getWorkspace();
        }

        //Not call from an Executor
        if (rootLauncher == null) {
            rootLauncher = build.getProject().getSomeWorkspace();
        }

        try {
            GradleConsoleAnnotator gca = new GradleConsoleAnnotator(
                    listener.getLogger(), build.getCharset());
            int r;
            try {
                r = launcher.launch().cmds(args).envs(env).stdout(gca)
                        .pwd(rootLauncher).join();
            } finally {
                gca.forceEol();
            }
            boolean success = r == 0;
            // if the build is successful then set it as success otherwise as a failure.
            build.setResult(Result.SUCCESS);
            if (!success) {
                build.setResult(Result.FAILURE);
            }
            return success;
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError("command execution failed"));
            build.setResult(Result.FAILURE);
            return false;
        }
    }

    /**
     * Returns the current {@link Node} on which we are buildling.
     */
    protected Node getCurrentNode() {
        return Executor.currentExecutor().getOwner().getNode();
    }

    private static FilePath normalizePath(AbstractBuild<?, ?> build, EnvVars env, String path)
    {
        FilePath normalizedPath = null;
        if (path != null && path.trim().length() != 0) {
            String pathNormalized = normalizeString(build, env, path);
            normalizedPath = new FilePath(build.getModuleRoot(), pathNormalized);
        }
        return normalizedPath;
    }

    private static String normalizeString(AbstractBuild<?, ?> build, EnvVars env, String str)
    {
        String strNormalized = str.replaceAll("[\t\r\n]+", " ").trim();
        strNormalized = Util.replaceMacro(strNormalized, env);
        strNormalized = Util.replaceMacro(strNormalized, build.getBuildVariableResolver());
        return strNormalized;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @CopyOnWrite
        private volatile GradleInstallation[] installations = new GradleInstallation[0];

        public DescriptorImpl() {
            load();
        }

        protected DescriptorImpl(Class<? extends Gradle> clazz) {
            super(clazz);
        }

        /**
         * Obtains the {@link GradleInstallation.DescriptorImpl} instance.
         */
        public GradleInstallation.DescriptorImpl getToolDescriptor() {
            return ToolInstallation.all().get(GradleInstallation.DescriptorImpl.class);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        protected void convert(Map<String, Object> oldPropertyBag) {
            if (oldPropertyBag.containsKey("installations")) {
                installations = (GradleInstallation[]) oldPropertyBag.get("installations");
            }
        }

        @Override
        public String getHelpFile() {
            return "/plugin/gradle/help.html";
        }

        @Override
        public String getDisplayName() {
            return Messages.step_displayName();
        }

        public GradleInstallation[] getInstallations() {
            return installations;
        }

        public void setInstallations(GradleInstallation... installations) {
            this.installations = installations;
            save();
        }

        @Override
        public Gradle newInstance(StaplerRequest request, JSONObject formData) throws FormException {

            // "flatten" formData for useWrapper radioBlocks
            JSONObject useWrapper = formData.getJSONObject("useWrapper");
            boolean wrapper = useWrapper.getBoolean("value");
            useWrapper.remove("value");
            for (String key : (Set<String>) useWrapper.keySet()) {
                formData.put(key, useWrapper.get(key));
            }
            formData.put("useWrapper", wrapper);
            JSONObject useLauncher = formData.getJSONObject("useLauncherJar");
            if (!useLauncher.isNullObject()) {
                formData.put("launcherJar", useLauncher.get("launcherJar"));
                formData.put("useLauncherJar", !useLauncher.isEmpty());
            }

            return (Gradle) request.bindJSON(clazz, formData);
        }
    }

}
