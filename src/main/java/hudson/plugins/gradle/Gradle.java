package hudson.plugins.gradle;

import hudson.*;
import hudson.model.*;
import hudson.remoting.Callable;
import hudson.slaves.NodeSpecific;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import hudson.util.ArgumentListBuilder;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;


public class Gradle extends Builder {


    /**
     * The GradleBuilder build step description
     */
    private final String description;

    /**
     * The GradleBuilder command line switches
     */
    private final String switches;

    /**
     * The GradleBuilder tasks
     */
    private final String tasks;


    private final String rootBuildScriptDir;

    /**
     * The GradleBuilder build file path
     */
    private final String buildFile;

    /**
     * Identifies {@link GradleInstallation} to be used.
     */
    private final String gradleName;

    @DataBoundConstructor
    public Gradle(String description, String switches, String tasks, String rootBuildScriptDir, String buildFile, String gradleName) {
        this.description = description;
        this.switches = switches;
        this.tasks = tasks;
        this.gradleName = gradleName;
        this.rootBuildScriptDir = rootBuildScriptDir;
        this.buildFile = buildFile;
    }


    public String getSwitches() {
        return switches;
    }

    public String getBuildFile() {
        return buildFile;
    }

    public String getGradleName() {
        return gradleName;
    }

    public String getTasks() {
        return tasks;
    }

    public String getDescription() {
        return description;
    }

    public String getRootBuildScriptDir() {
        return rootBuildScriptDir;
    }

    /**
     * Gets the GradleBuilder to invoke,
     * or null to invoke the default one.
     */
    public GradleInstallation getGradle() {
        for (GradleInstallation i : getDescriptor().getInstallations()) {
            if (gradleName != null && i.getName().equals(gradleName))
                return i;
        }
        return null;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        EnvVars env = build.getEnvironment(listener);

        String normalizedSwitches = switches.replaceAll("[\t\r\n]+", " ");
        normalizedSwitches = Util.replaceMacro(normalizedSwitches, env);
        normalizedSwitches = Util.replaceMacro(normalizedSwitches, build.getBuildVariables());

        String normalizedTasks = tasks.replaceAll("[\t\r\n]+", " ");

        ArgumentListBuilder args = new ArgumentListBuilder();

        GradleInstallation ai = getGradle();
        if (ai == null) {
            args.add(launcher.isUnix() ? "gradle" : "gradle.bat");
        } else {
            ai = ai.forNode(Computer.currentComputer().getNode(), listener);
            ai = ai.forEnvironment(env);
            String exe = ai.getExecutable(launcher);
            if (exe == null) {
                listener.fatalError("ERROR");
                return false;
            }
            args.add(exe);
        }
        args.addKeyValuePairs("-D", build.getBuildVariables());
        args.addTokenized(normalizedSwitches);
        args.addTokenized(normalizedTasks);

        if (buildFile != null && buildFile.trim().length() != 0) {
            String buildFileNormalized = Util.replaceMacro(buildFile.trim(), env);
            args.add("-b");
            args.add(buildFileNormalized);
        }


        if (ai != null)
            env.put("GRADLE_HOME", ai.getHome());

        if (!launcher.isUnix()) {
            // on Windows, executing batch file can't return the correct error code,
            // so we need to wrap it into cmd.exe.
            // double %% is needed because we want ERRORLEVEL to be expanded after
            // batch file executed, not before. This alone shows how broken Windows is...
            args.prepend("cmd.exe", "/C");
            args.add("&&", "exit", "%%ERRORLEVEL%%");
        }

        FilePath rootLauncher = null;
        if (rootBuildScriptDir != null && rootBuildScriptDir.trim().length() != 0) {
            String rootBuildScriptNormalized = Util.replaceMacro(rootBuildScriptDir.trim(), env);
            rootBuildScriptNormalized = Util.replaceMacro(rootBuildScriptNormalized, build.getBuildVariableResolver());
            rootLauncher = new FilePath(build.getModuleRoot(), rootBuildScriptNormalized);
        } else {
            rootLauncher = build.getModuleRoot();
        }


        try {
            int r = launcher.launch().cmds(args).envs(env).stdout(listener).pwd(rootLauncher).join();
            return r == 0;
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError("command execution failed"));
            return false;
        }
    }



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

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        protected void convert(Map<String, Object> oldPropertyBag) {
            if (oldPropertyBag.containsKey("installations"))
                installations = (GradleInstallation[]) oldPropertyBag.get("installations");
        }

        public String getHelpFile() {
            return "/plugin/gradle/help.html";
        }

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
