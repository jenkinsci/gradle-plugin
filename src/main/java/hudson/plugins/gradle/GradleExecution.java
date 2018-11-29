package hudson.plugins.gradle;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import hudson.util.VariableResolver;

/**
 * Helper for execution of an Gradle build.
 * Encapsulates the real gradle execution independent of build type.
 */
public final class GradleExecution {

    private String switches;
    private String tasks;
    private String rootBuildScriptDir;
    private String buildFile;
    private String gradleName;
    private boolean useWrapper;
    private boolean makeExecutable;
    private boolean useWorkspaceAsHome;
    private String wrapperLocation;
    private String systemProperties;
    private boolean passAllAsSystemProperties;
    private String logEncoding;

    private String projectProperties;
    private boolean passAllAsProjectProperties;

    private final GradleInstallationProvider installationProvider;

    public GradleExecution(
            String switches, 
            String tasks, 
            String rootBuildScriptDir, 
            String buildFile,
            String gradleName, 
            boolean useWrapper, 
            boolean makeExecutable, 
            boolean useWorkspaceAsHome,
            String wrapperLocation, 
            String systemProperties,
            boolean passAllAsSystemProperties, 
            String projectProperties, 
            boolean passAllAsProjectProperties,
            GradleInstallationProvider installationProvider,
            String logEncoding) {
        
        this.switches = switches;
        this.tasks = tasks;
        this.rootBuildScriptDir = rootBuildScriptDir;
        this.buildFile = buildFile;
        this.gradleName = gradleName;
        this.useWrapper = useWrapper;
        this.makeExecutable = makeExecutable;
        this.useWorkspaceAsHome = useWorkspaceAsHome;
        this.wrapperLocation = wrapperLocation;
        this.systemProperties = systemProperties;
        this.passAllAsSystemProperties = passAllAsSystemProperties;
        this.projectProperties = projectProperties;
        this.passAllAsProjectProperties = passAllAsProjectProperties;
        this.installationProvider = installationProvider;
        this.logEncoding = logEncoding;
    }

    public GradleInstallation getGradle() {
        for (GradleInstallation i : installationProvider.getInstallations()) {
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


    /**
     * Executes the gradle build.
     * 
     * @return Returns <code>true</code> if gradle was executed successfully and returned return code 0.
     */
    boolean performTask(
            boolean dryRun,
            Run<?, ?> build,
            Launcher launcher,
            TaskListener listener,
            FilePath workspace,
            FilePath moduleRoot,
            VariableResolver<String> resolver,
            EnvVars env,
            Map<String, String> buildVariables) throws InterruptedException, IOException {

        GradleLogger gradleLogger = new GradleLogger(listener);
        gradleLogger.info("Launching build.");

        //Switches
        String normalizedSwitches = getNormalized(switches, resolver, "GRADLE_EXT_SWITCHES");

        //Add dry-run switch if needed
        if (dryRun) {
            normalizedSwitches = normalizedSwitches + " --dry-run";
        }

        //Tasks
        String normalizedTasks = getNormalized(tasks, resolver, "GRADLE_EXT_TASKS");

        FilePath normalizedRootBuildScriptDir = getNormalizedRootBuildScriptDir(build, resolver, moduleRoot);

        //Build arguments
        ArgumentListBuilder args = new ArgumentListBuilder();
        if (useWrapper) {
            FilePath gradleWrapperFile = findGradleWrapper(normalizedRootBuildScriptDir, build, launcher, listener, resolver, moduleRoot);
            if (gradleWrapperFile == null) {
                gradleLogger.error("GradleWrapper not found.");
                build.setResult(Result.FAILURE);
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
                Computer computer = workspace.toComputer();
                Node node = computer == null ? null : computer.getNode();
                if (node != null) {
                    ai = ai.forNode(node, listener);
                    ai = ai.forEnvironment(env);
                    String exe = ai.getExecutable(launcher);
                    if (exe == null) {
                        gradleLogger.error("Can't retrieve the Gradle executable for gradle installation " + ai.getName() + " on node " + node.getNodeName());
                        build.setResult(Result.FAILURE);
                        return false;
                    }
                    env.put("GRADLE_HOME", ai.getHome());
                    args.add(exe);
                } else {
                    gradleLogger.error("Not in a build node.");
                    build.setResult(Result.FAILURE);
                    return false;
                }
            } else {
                //No gradle installation either, fall back to simple command
                args.add(launcher.isUnix() ? GradleInstallation.UNIX_GRADLE_COMMAND : GradleInstallation.WINDOWS_GRADLE_COMMAND);
            }
        }

        Set<String> sensitiveVars;
        if (build instanceof AbstractBuild) {
            sensitiveVars = ((AbstractBuild<?, ?>) build).getSensitiveBuildVariables();
        } else {
            sensitiveVars = Collections.emptySet();
        }

        args.addKeyValuePairsFromPropertyString("-D", systemProperties, resolver, sensitiveVars);
        args.addKeyValuePairsFromPropertyString("-P", projectProperties, resolver, sensitiveVars);

        if (passAllAsSystemProperties) {
            args.addKeyValuePairs("-D", buildVariables, sensitiveVars);
        }
        if (passAllAsProjectProperties) {
            args.addKeyValuePairs("-P", buildVariables, sensitiveVars);
        }

        args.addTokenized(normalizedSwitches);
        args.addTokenized(normalizedTasks);
        if (StringUtils.isNotBlank(buildFile)) {
            String buildFileNormalized = Util.replaceMacro(buildFile.trim(), resolver);
            args.add("-b");
            args.add(buildFileNormalized);
        }

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
            rootLauncher = workspace;
        }

        //Not call from an Executor
        if (rootLauncher == null && build instanceof AbstractBuild) {
            rootLauncher = ((AbstractBuild<?, ?>) build).getParent().getSomeWorkspace();
        }

        try {
            if (rootLauncher == null) {
                throw new IOException("Directory for gradle execution could not be determined.");
            }

            GradleConsoleAnnotator gca = new GradleConsoleAnnotator(
                listener.getLogger(),
                this.logEncoding == null ? build.getCharset() : Charset.forName(logEncoding)
            );

            int r;
            try {
                r = launcher
                    .launch()
                    .cmds(args)
                    .envs(env)
                    .stdout(gca)
                    .pwd(rootLauncher)
                    .join();
            } finally {
                gca.forceEol();
            }
            gradleLogger.info("Gradle finished with rc " + r);

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

    private FilePath findGradleWrapper(FilePath normalizedRootBuildScriptDir, Run<?, ?> build, Launcher launcher, TaskListener listener, VariableResolver<String> resolver, FilePath buildScriptRoot) throws IOException, InterruptedException {
        List<FilePath> possibleWrapperLocations = getPossibleWrapperLocations(build, launcher, resolver, normalizedRootBuildScriptDir, buildScriptRoot);
        String execName = (launcher.isUnix()) ? GradleInstallation.UNIX_GRADLE_WRAPPER_COMMAND : GradleInstallation.WINDOWS_GRADLE_WRAPPER_COMMAND;
        FilePath gradleWrapperFile = null;
        for (FilePath possibleWrapperLocation : possibleWrapperLocations) {
            FilePath possibleGradleWrapperFile = new FilePath(possibleWrapperLocation, execName);
            if (possibleGradleWrapperFile.exists()) {
                gradleWrapperFile = possibleGradleWrapperFile;
                break;
            }
        }
        if (gradleWrapperFile == null) {
            listener.fatalError("The Gradle wrapper has not been found in these directories: %s", Joiner.on(", ").join(possibleWrapperLocations));
        }
        return gradleWrapperFile;
    }

    private FilePath getNormalizedRootBuildScriptDir(Run<?, ?> build, VariableResolver<String> resolver, FilePath moduleRoot) {
        FilePath normalizedRootBuildScriptDir = null;
        if (rootBuildScriptDir != null && rootBuildScriptDir.trim().length() != 0) {
            String rootBuildScriptNormalized = replaceWhitespaceBySpace(rootBuildScriptDir.trim());
            rootBuildScriptNormalized = Util.replaceMacro(rootBuildScriptNormalized.trim(), resolver);
            normalizedRootBuildScriptDir = new FilePath(moduleRoot, rootBuildScriptNormalized);
        }
        return normalizedRootBuildScriptDir;
    }

    private static String getNormalized(String args, VariableResolver<String> resolver, String contributingEnvironmentVariable) {
        String extraArgs = resolver.resolve(contributingEnvironmentVariable);
        String normalizedArgs = append(args, extraArgs);
        normalizedArgs = replaceWhitespaceBySpace(normalizedArgs);
        normalizedArgs = Util.replaceMacro(normalizedArgs, resolver);
        return normalizedArgs;
    }

    private static String replaceWhitespaceBySpace(String argument) {
        return argument.replaceAll("[\t\r\n]+", " ");
    }

    private List<FilePath> getPossibleWrapperLocations(Run<?, ?> build, Launcher launcher, VariableResolver<String> resolver, FilePath normalizedRootBuildScriptDir, FilePath moduleRoot) throws IOException, InterruptedException {
        if (wrapperLocation != null && wrapperLocation.trim().length() != 0) {
            // Override with provided relative path to gradlew
            String wrapperLocationNormalized = wrapperLocation.trim().replaceAll("[\t\r\n]+", "");
            wrapperLocationNormalized = Util.replaceMacro(wrapperLocationNormalized.trim(), resolver);
            return ImmutableList.of(new FilePath(moduleRoot, wrapperLocationNormalized));
        } else if (buildFile != null && !buildFile.isEmpty()) {
            // Check if the target project is located not at the root dir
            FilePath parentOfBuildFile = new FilePath(normalizedRootBuildScriptDir == null ? moduleRoot : normalizedRootBuildScriptDir, buildFile).getParent();
            if (parentOfBuildFile != null && !parentOfBuildFile.equals(moduleRoot)) {
                return ImmutableList.of(parentOfBuildFile, moduleRoot);
            }
        }
        return ImmutableList.of(moduleRoot);
    }
}
