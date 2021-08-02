package hudson.plugins.gradle;

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
import hudson.util.VariableResolver;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Gregory Boissinot
 */
public class Gradle extends Builder {

    private String switches;
    private String tasks;
    private String rootBuildScriptDir;
    private String buildFile;
    private String gradleName;
    private boolean useWrapper;
    private boolean makeExecutable;
    private boolean useWorkspaceAsHome;
    private String wrapperLocation;
    private transient Boolean passAsProperties;
    private String systemProperties;
    private boolean passAllAsSystemProperties;

    private String projectProperties;
    private boolean passAllAsProjectProperties;

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

    public String getSystemProperties() {
        return systemProperties;
    }

    @DataBoundSetter
    public void setSystemProperties(String systemProperties) {
        this.systemProperties = Util.fixEmptyAndTrim(systemProperties);
    }

    public boolean isPassAllAsSystemProperties() {
        return passAllAsSystemProperties;
    }

    @DataBoundSetter
    public void setPassAllAsSystemProperties(boolean passAllAsSystemProperties) {
        this.passAllAsSystemProperties = passAllAsSystemProperties;
    }

    public String getProjectProperties() {
        return projectProperties;
    }

    @DataBoundSetter
    public void setProjectProperties(String projectProperties) {
        this.projectProperties = projectProperties;
    }

    public boolean isPassAllAsProjectProperties() {
        return passAllAsProjectProperties;
    }

    @DataBoundSetter
    public void setPassAllAsProjectProperties(boolean passAllAsProjectProperties) {
        this.passAllAsProjectProperties = passAllAsProjectProperties;
    }

    public GradleInstallation getGradle() {
        for (GradleInstallation i : getDescriptor().getInstallations()) {
            if (gradleName != null && i.getName().equals(gradleName)) {
                return i;
            }
        }
        return null;
    }

    /**
     * Turns a null string into a blank string.
     */
    private static String null2Blank(String input) {
        return input != null ? input : "";
    }

    /**
     * Appends text to a possibly null string.
     */
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
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {

        GradleLogger gradleLogger = new GradleLogger(listener);
        gradleLogger.info("Launching build.");

        EnvVars env = build.getEnvironment(listener);
        VariableResolver.Union<String> resolver = new VariableResolver.Union<>(new VariableResolver.ByMap<>(env), build.getBuildVariableResolver());

        //Switches
        String normalizedSwitches = getNormalized(switches, resolver, "GRADLE_EXT_SWITCHES");

        //Tasks
        String normalizedTasks = getNormalized(tasks, resolver, "GRADLE_EXT_TASKS");

        FilePath normalizedRootBuildScriptDir = getNormalizedRootBuildScriptDir(build, resolver);

        //Build arguments
        ArgumentListBuilder args = new ArgumentListBuilder();
        if (useWrapper) {
            FilePath gradleWrapperFile = findGradleWrapper(normalizedRootBuildScriptDir, build, launcher, listener, resolver);
            if (gradleWrapperFile == null) {
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
        args.addKeyValuePairsFromPropertyString("-D", getSystemProperties(), resolver, sensitiveVars);
        if (isPassAllAsSystemProperties()) {
            args.addKeyValuePairs("-D", build.getBuildVariables(), sensitiveVars);
        }
        args.addKeyValuePairsFromPropertyString("-P", getProjectProperties(), resolver, sensitiveVars);
        if (isPassAllAsProjectProperties()) {
            args.addKeyValuePairs("-P", build.getBuildVariables(), sensitiveVars);
        }
        args.addTokenized(normalizedSwitches);
        args.addTokenized(normalizedTasks);
        if (StringUtils.isNotBlank(buildFile)) {
            String buildFileNormalized = Util.replaceMacro(buildFile.trim(), resolver);
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
            DefaultBuildScanPublishedListener buildScanListener = new DefaultBuildScanPublishedListener(build);
            GradleConsoleAnnotator gca = new GradleConsoleAnnotator(listener.getLogger(), build.getCharset(), true, buildScanListener);

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

    private FilePath findGradleWrapper(FilePath normalizedRootBuildScriptDir, AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, VariableResolver<String> resolver) throws IOException, InterruptedException {
        List<FilePath> possibleWrapperLocations = getPossibleWrapperLocations(build, launcher, resolver, normalizedRootBuildScriptDir);
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
            listener.fatalError("The Gradle wrapper has not been found in these directories: %s", possibleWrapperLocations.stream().map(Object::toString).collect(Collectors.joining(", ")));
        }
        return gradleWrapperFile;
    }

    private FilePath getNormalizedRootBuildScriptDir(AbstractBuild<?, ?> build, VariableResolver<String> resolver) {
        FilePath normalizedRootBuildScriptDir = null;
        if (rootBuildScriptDir != null && rootBuildScriptDir.trim().length() != 0) {
            String rootBuildScriptNormalized = replaceWhitespaceBySpace(rootBuildScriptDir.trim());
            rootBuildScriptNormalized = Util.replaceMacro(rootBuildScriptNormalized.trim(), resolver);
            normalizedRootBuildScriptDir = new FilePath(build.getModuleRoot(), rootBuildScriptNormalized);
        }
        return normalizedRootBuildScriptDir;
    }

    private String getNormalized(String args, VariableResolver<String> resolver, String contributingEnvironmentVariable) {
        String extraArgs = resolver.resolve(contributingEnvironmentVariable);
        String normalizedArgs = append(args, extraArgs);
        normalizedArgs = replaceWhitespaceBySpace(normalizedArgs);
        normalizedArgs = Util.replaceMacro(normalizedArgs, resolver);
        return normalizedArgs;
    }

    private String replaceWhitespaceBySpace(String argument) {
        return argument.replaceAll("[\t\r\n]+", " ");
    }

    private List<FilePath> getPossibleWrapperLocations(AbstractBuild<?, ?> build, Launcher launcher, VariableResolver<String> resolver, FilePath normalizedRootBuildScriptDir) throws IOException, InterruptedException {
        FilePath moduleRoot = build.getModuleRoot();
        if (wrapperLocation != null && wrapperLocation.trim().length() != 0) {
            // Override with provided relative path to gradlew
            String wrapperLocationNormalized = wrapperLocation.trim().replaceAll("[\t\r\n]+", "");
            wrapperLocationNormalized = Util.replaceMacro(wrapperLocationNormalized.trim(), resolver);
            return Collections.singletonList(new FilePath(moduleRoot, wrapperLocationNormalized));
        } else if (buildFile != null && !buildFile.isEmpty()) {
            // Check if the target project is located not at the root dir
            FilePath parentOfBuildFile = new FilePath(normalizedRootBuildScriptDir == null ? moduleRoot : normalizedRootBuildScriptDir, buildFile).getParent();
            if (parentOfBuildFile != null && !parentOfBuildFile.equals(moduleRoot)) {
                List<FilePath> locations = new ArrayList<>();
                Collections.addAll(locations, parentOfBuildFile, moduleRoot);
                return Collections.unmodifiableList(locations);
            }
        }
        return Collections.singletonList(moduleRoot);
    }

    private Object readResolve() {
        if (fromRootBuildScriptDir) {
            wrapperLocation = rootBuildScriptDir;
        }
        if (passAsProperties != null) {
            if (passAsProperties) {
                passAllAsProjectProperties = true;
            } else {
                passAllAsSystemProperties = true;
            }
        }
        return this;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    @Symbol("gradle")
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
        setPassAllAsProjectProperties(passAsProperties);
        setPassAllAsSystemProperties(!passAsProperties);
    }
}
