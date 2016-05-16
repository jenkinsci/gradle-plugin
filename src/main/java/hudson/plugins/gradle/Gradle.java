package hudson.plugins.gradle;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tools.ToolInstallation;
import hudson.util.ArgumentListBuilder;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.lib.dryrun.DryRun;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 * @author Gregory Boissinot
 */
public class Gradle extends Builder implements DryRun {

    // TODO: Remove when baseline 1.653+
    @SuppressFBWarnings(value="NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification="https://github.com/jenkinsci/jenkins/commit/bb7c8fcedbcc9b51c5b1bb5b32810af5ac6b1ffb")
    static @NonNull Jenkins getJenkins() {
        return Jenkins.getInstance();
    }

    private final String description;
    private final String switches;
    private final String tasks;
    private final String rootBuildScriptDir;
    private final String buildFile;
    private final String gradleName;
    private final boolean useWrapper;
    private final boolean makeExecutable;
    private final boolean fromRootBuildScriptDir;
    private final boolean useWorkspaceAsHome;
    private final boolean passAsProperties;

    @DataBoundConstructor
    public Gradle(String description, String switches, String tasks, String rootBuildScriptDir, String buildFile,
                  String gradleName, boolean useWrapper, boolean makeExecutable, boolean fromRootBuildScriptDir,
                  boolean useWorkspaceAsHome, boolean passAsProperties) {
        this.description = description;
        this.switches = switches;
        this.tasks = tasks;
        this.gradleName = gradleName;
        this.rootBuildScriptDir = rootBuildScriptDir;
        this.buildFile = buildFile;
        this.useWrapper = useWrapper;
        this.makeExecutable = makeExecutable;
        this.fromRootBuildScriptDir = fromRootBuildScriptDir;
        this.useWorkspaceAsHome = useWorkspaceAsHome;
        this.passAsProperties = passAsProperties;
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

    @SuppressWarnings("unused")
    public boolean isPassAsProperties() {
        return passAsProperties;
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
            //We are using the wrapper and don't care about the installed gradle versions
            String execName = (launcher.isUnix()) ? GradleInstallation.UNIX_GRADLE_WRAPPER_COMMAND : GradleInstallation.WINDOWS_GRADLE_WRAPPER_COMMAND;
            FilePath gradleWrapperFile;
            if (fromRootBuildScriptDir && (normalizedRootBuildScriptDir != null)) {
                gradleWrapperFile = new FilePath(normalizedRootBuildScriptDir, execName);
            } else {
                gradleWrapperFile = new FilePath(build.getModuleRoot(), execName); // Fallback path

                // It's possible that a user wants to use gradle wrapper of a project which is located
                // not at a repo's root. Example:
                //    my-big-repo
                //        |__my-project
                //               |__<my files>
                //               |__gradlew
                // We want to point to the gradlew located at that project then.

                if (buildFile != null && !buildFile.isEmpty()) {
                    // Check if the target project is located not at the root dir
                    char fileSeparator = launcher.isUnix() ? '/' : '\\';
                    int i = buildFile.lastIndexOf(fileSeparator);
                    if (i > 0) {
                        // Check if there is a wrapper script at the target project's dir.
                        FilePath baseDir = build.getModuleRoot();
                        FilePath candidate = new FilePath(baseDir, buildFile.substring(0, i));
                        if (candidate.isDirectory() && new FilePath(candidate, execName).exists()) {
                            // Use gradle wrapper file from the target project.
                            gradleWrapperFile = new FilePath(candidate, execName);
                        }
                    }
                }
            }

            if (makeExecutable) {
                gradleWrapperFile.chmod(0744);
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
            return success;
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError("command execution failed"));
            build.setResult(Result.FAILURE);
            return false;
        }
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
            return Arrays.copyOf(installations, installations.length);
        }

        public void setInstallations(GradleInstallation... installations) {
            this.installations = installations;
            save();
        }
    }
}
