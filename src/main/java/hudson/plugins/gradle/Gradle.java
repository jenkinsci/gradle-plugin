package hudson.plugins.gradle;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tools.ToolInstallation;
import hudson.util.ArgumentListBuilder;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.lib.dryrun.DryRun;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * @author Gregory Boissinot
 */
public class Gradle extends Builder implements DryRun {

    private String switches;
    private String tasks;
    private String rootBuildScriptDir;
    private String buildFile;
    private String gradleName;
    private boolean useWrapper;
    private boolean makeExecutable;
    private boolean useWorkspaceAsHome;
    private String wrapperLocation;
    private boolean passAsProperties;

    private transient boolean fromRootBuildScriptDir;

    @DataBoundConstructor
    public Gradle() {
    }

    @SuppressWarnings("unused")
    public String getSwitches() {
        return switches;
    }

    @DataBoundSetter
    public void setSwitches(String switches) {
        this.switches = switches;
    }

    @SuppressWarnings("unused")
    public String getTasks() {
        return tasks;
    }

    @DataBoundSetter
    public void setTasks(String tasks) {
        this.tasks = tasks;
    }

    @SuppressWarnings("unused")
    public String getRootBuildScriptDir() {
        return rootBuildScriptDir;
    }

    @DataBoundSetter
    public void setRootBuildScriptDir(String rootBuildScriptDir) {
        this.rootBuildScriptDir = rootBuildScriptDir;
    }

    @SuppressWarnings("unused")
    public String getBuildFile() {
        return buildFile;
    }

    @DataBoundSetter
    public void setBuildFile(String buildFile) {
        this.buildFile = buildFile;
    }

    @SuppressWarnings("unused")
    public String getGradleName() {
        return gradleName;
    }

    @DataBoundSetter
    public void setGradleName(String gradleName) {
        this.gradleName = gradleName;
    }

    @SuppressWarnings("unused")
    public boolean isUseWrapper() {
        return useWrapper;
    }

    @DataBoundSetter
    public void setUseWrapper(boolean useWrapper) {
        this.useWrapper = useWrapper;
    }

    @SuppressWarnings("unused")
    public boolean isMakeExecutable() {
        return makeExecutable;
    }

    @DataBoundSetter
    public void setMakeExecutable(boolean makeExecutable) {
        this.makeExecutable = makeExecutable;
    }

    @SuppressWarnings("unused")
    public boolean isUseWorkspaceAsHome() {
        return useWorkspaceAsHome;
    }

    @DataBoundSetter
    public void setUseWorkspaceAsHome(boolean useWorkspaceAsHome) {
        this.useWorkspaceAsHome = useWorkspaceAsHome;
    }

    @SuppressWarnings("unused")
    public String getWrapperLocation() {
        return wrapperLocation;
    }

    @DataBoundSetter
    public void setWrapperLocation(String wrapperLocation) {
        this.wrapperLocation = wrapperLocation;
    }

    @SuppressWarnings("unused")
    public boolean isPassAsProperties() {
        return passAsProperties;
    }

    @DataBoundSetter
    public void setPassAsProperties(boolean passAsProperties) {
        this.passAsProperties = passAsProperties;
    }

    public GradleInstallation getGradle() {
        for (GradleInstallation i : getDescriptor().getInstallations()) {
            if (gradleName != null && i.getName().equals(gradleName)) {
                return i;
            }
        }
        return null;
    }

    /** Turns a null string into a blanck string. */
    private static String null2Blank(String input) {
        return input != null ? input : "";
    }

    /** Appends text to a possibly null string. */
    private static String append(String input, String textToAppend) {
        if (StringUtils.isBlank(input)) {
            return null2Blank(textToAppend);
        }
        if (StringUtils.isBlank(textToAppend)) {
            return null2Blank(input);
        }
        return input + " " + textToAppend;
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
        String normalizedSwitches = append(switches, extraSwitches);
        normalizedSwitches = normalizedSwitches.replaceAll("[\t\r\n]+", " ");
        normalizedSwitches = Util.replaceMacro(normalizedSwitches, env);
        normalizedSwitches = Util.replaceMacro(normalizedSwitches, build.getBuildVariables());

        //Add dry-run switch if needed
        if (dryRun) {
            normalizedSwitches = normalizedSwitches + " --dry-run";
        }

        //Tasks
        String extraTasks = env.get("GRADLE_EXT_TASKS");
        String normalizedTasks = append(tasks, extraTasks);
        normalizedTasks = normalizedTasks.replaceAll("[\t\r\n]+", " ");
        normalizedTasks = Util.replaceMacro(normalizedTasks, env);
        normalizedTasks = Util.replaceMacro(normalizedTasks, build.getBuildVariables());

        FilePath normalizedRootBuildScriptDir = null;
        if (rootBuildScriptDir != null && rootBuildScriptDir.trim().length() != 0) {
            String rootBuildScriptNormalized = rootBuildScriptDir.trim().replaceAll("[\t\r\n]+", " ");
            rootBuildScriptNormalized = Util.replaceMacro(rootBuildScriptNormalized.trim(), env);
            rootBuildScriptNormalized = Util.replaceMacro(rootBuildScriptNormalized, build.getBuildVariableResolver());
            normalizedRootBuildScriptDir = new FilePath(build.getModuleRoot(), rootBuildScriptNormalized);
        }

        //Build arguments
        ArgumentListBuilder args = new ArgumentListBuilder();
        if (useWrapper) {
            List<FilePath> possibleWrapperLocations = getPossibleWrapperLocations(build, launcher, env, normalizedRootBuildScriptDir);
            String execName = (launcher.isUnix()) ? GradleInstallation.UNIX_GRADLE_WRAPPER_COMMAND : GradleInstallation.WINDOWS_GRADLE_WRAPPER_COMMAND;
            FilePath gradleWrapperFile = null;
            for (FilePath possibleWrapperLocation : possibleWrapperLocations) {
                final FilePath possibleGradleWrapperFile = new FilePath(possibleWrapperLocation, execName);
                if (possibleGradleWrapperFile.exists()) {
                    gradleWrapperFile = possibleGradleWrapperFile;
                    break;
                }
            }
            if (gradleWrapperFile == null) {
                listener.fatalError("The Gradle wrapper has not been found in these directories: %s", Joiner.on(", ").join(possibleWrapperLocations));
                return false;
            }
            if (makeExecutable) {
                gradleWrapperFile.chmod(0755);
            }
            args.add(gradleWrapperFile.getRemote());
        } else {
            //Look for a gradle installation
            GradleInstallation ai = getGradle();
            if (ai != null) {
                Computer computer = Computer.currentComputer();
                Node node = computer != null ? computer.getNode() : null;
                if (node != null) {
                    ai = ai.forNode(node, listener);
                    ai = ai.forEnvironment(env);
                    String exe = ai.getExecutable(launcher);
                    if (exe == null) {
                        gradleLogger.error("Can't retrieve the Gradle executable.");
                        return false;
                    }
                    env.put("GRADLE_HOME", ai.getHome());
                    args.add(exe);
                } else {
                    gradleLogger.error("Not in a build node.");
                    return false;
                }
            } else {
                //No gradle installation either, fall back to simple command
                args.add(launcher.isUnix() ? GradleInstallation.UNIX_GRADLE_COMMAND : GradleInstallation.WINDOWS_GRADLE_COMMAND);
            }
        }


        Set<String> sensitiveVars = build.getSensitiveBuildVariables();
        args.addKeyValuePairs(passPropertyOption(), fixParameters(build.getBuildVariables()), sensitiveVars);
        args.addTokenized(normalizedSwitches);
        args.addTokenized(normalizedTasks);
        if (buildFile != null && buildFile.trim().length() != 0) {
            String buildFileNormalized = Util.replaceMacro(buildFile.trim(), env);
            args.add("-b");
            args.add(buildFileNormalized);
        }

        final FilePath workspace = build.getWorkspace();
        if (useWorkspaceAsHome && workspace != null) {
            // Make user home relative to the workspace, so that files aren't shared between builds
            env.put("GRADLE_USER_HOME", workspace.getRemote());
        }

        if (!launcher.isUnix()) {
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
            String scanUrl = gca.getScanUrl();
            if (StringUtils.isNotEmpty(scanUrl)) {
                build.addAction(new BuildScanAction(scanUrl));
            }
            return success;
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError("command execution failed"));
            build.setResult(Result.FAILURE);
            return false;
        }
    }

    private List<FilePath> getPossibleWrapperLocations(AbstractBuild<?, ?> build, Launcher launcher, EnvVars env, FilePath normalizedRootBuildScriptDir) throws IOException, InterruptedException {
        FilePath moduleRoot = build.getModuleRoot();
        if (wrapperLocation != null && wrapperLocation.trim().length() != 0) {
            // Override with provided relative path to gradlew
            String wrapperLocationNormalized = wrapperLocation.trim().replaceAll("[\t\r\n]+", "");
            wrapperLocationNormalized = Util.replaceMacro(wrapperLocationNormalized.trim(), env);
            wrapperLocationNormalized = Util.replaceMacro(wrapperLocationNormalized, build.getBuildVariableResolver());
            return ImmutableList.of(new FilePath(moduleRoot, wrapperLocationNormalized));
        } else if (buildFile != null && !buildFile.isEmpty()) {
            // Check if the target project is located not at the root dir
            char fileSeparator = launcher.isUnix() ? '/' : '\\';
            int i = buildFile.lastIndexOf(fileSeparator);
            if (i > 0) {
                // Check if there is a wrapper script at the target project's dir.
                FilePath candidate = new FilePath(normalizedRootBuildScriptDir == null ? moduleRoot : normalizedRootBuildScriptDir, buildFile.substring(0, i));
                return ImmutableList.of(candidate, moduleRoot);
            }
        }
        return ImmutableList.of(moduleRoot);
    }

    private Object readResolve() {
        if (fromRootBuildScriptDir) {
            wrapperLocation = rootBuildScriptDir;
        }
        return this;
    }

    private String passPropertyOption() {
        return passAsProperties ? "-P" : "-D";
    }

    private Map<String, String> fixParameters(Map<String, String> parmas) {
        Map<String, String> result = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : parmas.entrySet()) {
            String value = entry.getValue();
            if (isValue2Escape(value)) {
                result.put(entry.getKey(), "\"" + value + "\"");
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    private boolean isValue2Escape(String value) {
        if (value == null) {
            return false;
        }
        if (value.trim().length() == 0) {
            return false;
        }
        return value.contains("<") || value.contains(">");
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension @Symbol("gradle")
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
            return Arrays.copyOf(installations, installations.length);
        }

        public void setInstallations(GradleInstallation... installations) {
            this.installations = installations;
            save();
        }
    }

    @Deprecated
    public Gradle(String switches, String tasks, String rootBuildScriptDir, String buildFile,
                  String gradleName, boolean useWrapper, boolean makeExecutable, String wrapperLocation,
                  boolean useWorkspaceAsHome, boolean passAsProperties) {
        setSwitches(switches);
        setTasks(tasks);
        setRootBuildScriptDir(rootBuildScriptDir);
        setBuildFile(buildFile);
        setUseWrapper(useWrapper);
        setGradleName(gradleName); // May be null
        setWrapperLocation(wrapperLocation); // May be null
        setMakeExecutable(makeExecutable);
        setUseWorkspaceAsHome(useWorkspaceAsHome);
        setPassAsProperties(passAsProperties);
    }
}
