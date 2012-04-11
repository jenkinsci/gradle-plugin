package hudson.plugins.gradle;

import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.util.ArgumentListBuilder;
import hudson.util.ClasspathBuilder;
import hudson.util.JVMBuilder;
import net.sf.json.JSONObject;
import org.jenkinsci.lib.dryrun.DryRun;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.FileFilter;
import java.io.IOException;
import java.util.Map;


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
    private final String wrapperScript;

    // Artifact of how Jelly/Stapler puts conditional variables in blocks, which NEED to map to a sub-Object.
    // The alternative would have been to mess with DescriptorImpl.getInstance
    public static class UsingWrapper {
        @DataBoundConstructor
        public UsingWrapper(String value, String gradleName, String wrapperScript) {
            this.gradleName = gradleName;
            this.wrapperScript = wrapperScript;
        }

        String gradleName;
        String wrapperScript;
    }

    @DataBoundConstructor
    public Gradle(String description, String switches, String tasks, String rootBuildScriptDir, String buildFile,
                  UsingWrapper usingWrapper) {
        this.description = description;
        this.switches = switches;
        this.tasks = tasks;
        this.rootBuildScriptDir = rootBuildScriptDir;
        this.buildFile = buildFile;
        this.useWrapper = usingWrapper != null && usingWrapper.wrapperScript!=null;
        this.gradleName = usingWrapper==null?null:usingWrapper.gradleName; // May be null;
        this.wrapperScript = usingWrapper==null?null:usingWrapper.wrapperScript; // May be null
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
    public String getWrapperScript() {
        return wrapperScript;
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
        normalizedTasks = normalizedTasks.replaceAll("[\t\r\n]+", " ");
        normalizedTasks = Util.replaceMacro(normalizedTasks, env);
        normalizedTasks = Util.replaceMacro(normalizedTasks, build.getBuildVariables());

        // Resolve Gradle installation and java environment
        GradleInstallation ai = getGradle();
        if (ai == null) {
            launcher.getListener().error("Gradle installation not set, cannot build");
            build.setResult(Result.FAILURE);
            return false;
        }
        if (build.getEnvironment(listener).get("JAVA_HOME") == null) {
            launcher.getListener().error("Missing JAVA_HOME in build environment. Make sure a JDK is selected for the project.");
            build.setResult(Result.FAILURE);
            return false;
        }

        ai = ai.forNode(Computer.currentComputer().getNode(), listener);
        ai = ai.forEnvironment(env);


        // Build arguments, jvm options and classpath
        final ClasspathBuilder classpath = new ClasspathBuilder();
        final ArgumentListBuilder vmOptions = new ArgumentListBuilder();
        final ArgumentListBuilder gradleArgs = new ArgumentListBuilder();

        final FilePath libPath = new FilePath(new FilePath(launcher.getChannel(), ai.getHome()), "lib");
        for (FilePath file : libPath.list("gradle-launcher*.jar")) {
            classpath.add(file);
        }

        gradleArgs.addKeyValuePairs("-D", build.getBuildVariables());

        gradleArgs.addTokenized(normalizedSwitches);
        gradleArgs.addTokenized(normalizedTasks);
        if (buildFile != null && buildFile.trim().length() != 0) {
            String buildFileNormalized = Util.replaceMacro(buildFile.trim(), env);
            gradleArgs.add("-b");
            gradleArgs.add(buildFileNormalized);
        }
        if (ai != null) {
            env.put("GRADLE_HOME", ai.getHome());
        }

        FilePath rootLauncher;
        if (rootBuildScriptDir != null && rootBuildScriptDir.trim().length() != 0) {
            String rootBuildScriptNormalized = rootBuildScriptDir.trim().replaceAll("[\t\r\n]+", " ");
            rootBuildScriptNormalized = Util.replaceMacro(rootBuildScriptNormalized.trim(), env);
            rootBuildScriptNormalized = Util.replaceMacro(rootBuildScriptNormalized, build.getBuildVariableResolver());
            rootLauncher = new FilePath(build.getModuleRoot(), rootBuildScriptNormalized);
        } else {
            rootLauncher = build.getWorkspace();
        }

        //Not call from an Executor
        if (rootLauncher == null) {
            rootLauncher = build.getProject().getSomeWorkspace();
        }

        try {
            GradleConsoleAnnotator gca = new GradleConsoleAnnotator(listener.getLogger(), build.getCharset());
            boolean success;
            try {
                ArgumentListBuilder args = new ArgumentListBuilder();
                final FilePath javaHome = new FilePath(launcher.getChannel(), build.getEnvironment(listener).get("JAVA_HOME"));
                args.add(javaHome.child("bin").child("java").getRemote());
                args.add("-cp").add(classpath.toString());
                args.add(vmOptions.toCommandArray());
                args.add("org.gradle.launcher.GradleMain");
                args.add(gradleArgs.toCommandArray());

                final int returnCode = launcher.launch()
                        .cmds(args)
                        .envs(env)
                        .stdout(gca)
                        .pwd(rootLauncher)
                        .join();
                success = returnCode == 0;
            } finally {
                gca.forceEol();
            }
            // if the build is successful then set it as success otherwise as a failure.
            build.setResult(success ? Result.SUCCESS : Result.FAILURE);
            return success;
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError("command execution failed"));
            build.setResult(Result.FAILURE);
            return false;
        }
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
            return (Gradle) request.bindJSON(clazz, formData);
        }
    }

}
