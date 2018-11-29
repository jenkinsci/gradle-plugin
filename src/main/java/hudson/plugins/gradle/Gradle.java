package hudson.plugins.gradle;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import org.jenkinsci.Symbol;
import org.jenkinsci.lib.dryrun.DryRun;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tools.ToolInstallation;
import hudson.util.VariableResolver;


/**
 * {@link Builder} for Freestyle-Builds to execute Gradle.
 * 
 * @author Gregory Boissinot
 */
public class Gradle extends Builder implements DryRun, GradleInstallationProvider {

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
    
    private String logEncoding;

    @DataBoundConstructor
    public Gradle() {
    }

    @DataBoundSetter
    public void setLogEncoding(String logEncoding) {
        this.logEncoding = logEncoding;
    }
    
    public String getLogEncoding() {
        return logEncoding;
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
        VariableResolver<String> resolver= 
            new VariableResolver.Union<>(
                new VariableResolver.ByMap<>(env), 
                ((AbstractBuild) build).getBuildVariableResolver());
        
        return new GradleExecution(
            switches, 
            tasks, 
            rootBuildScriptDir, 
            buildFile,
            gradleName, 
            useWrapper, 
            makeExecutable, 
            useWorkspaceAsHome,
            wrapperLocation, 
            systemProperties,
            passAllAsSystemProperties, 
            projectProperties, 
            passAllAsProjectProperties,
            this,
            logEncoding
        ).performTask(
            dryRun, 
            build,
            launcher,
            listener,
            build.getWorkspace(), 
            build.getModuleRoot(), 
            resolver,
            env,
            build.getBuildVariables()
        );
        
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
    
    @Override
    public GradleInstallation[] getInstallations() {
        return ((DescriptorImpl) getDescriptor()).getInstallations();
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
