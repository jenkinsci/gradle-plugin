/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.gradle;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.collect.ImmutableSet;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.JDK;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tools.ToolInstallation;
import hudson.util.VariableResolver;
import jenkins.model.Jenkins;

/**
 * The Gradle-Step for a jenkins pipeline.
 * 
 * Only contains the configuration, the execution is delegated to an {@link GradleExecution}.
 * 
 * @author Sönke Küper
 */
public class GradleStep extends Step implements GradleInstallationProvider, Serializable {

    private static final long serialVersionUID = -8990886083758016221L;

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
    private String projectProperties;
    private String jdkName;
    private String logEncoding;
    
    @DataBoundConstructor
    public GradleStep () {
    }
    
    @Override
    public GradleInstallation[] getInstallations() {
        return ((GradleStepDescriptor) getDescriptor()).getInstallations();
    }

    @DataBoundSetter
    public void setLogEncoding(String logEncoding) {
        this.logEncoding = logEncoding;
    }
    
    public String getLogEncoding() {
        return logEncoding;
    }
    
    public String getSwitches() {
        return switches;
    }

    @DataBoundSetter
    public void setSwitches(String switches) {
        this.switches = switches;
    }

    public String getTasks() {
        return tasks;
    }

    @DataBoundSetter
    public void setTasks(String tasks) {
        this.tasks = tasks;
    }

    public String getRootBuildScriptDir() {
        return rootBuildScriptDir;
    }

    @DataBoundSetter
    public void setRootBuildScriptDir(String rootBuildScriptDir) {
        this.rootBuildScriptDir = rootBuildScriptDir;
    }

    public String getBuildFile() {
        return buildFile;
    }

    @DataBoundSetter
    public void setBuildFile(String buildFile) {
        this.buildFile = buildFile;
    }

    public String getGradleName() {
        return gradleName;
    }

    @DataBoundSetter
    public void setGradleName(String gradleName) {
        this.gradleName = gradleName;
    }

    public boolean isUseWrapper() {
        return useWrapper;
    }

    @DataBoundSetter
    public void setUseWrapper(boolean useWrapper) {
        this.useWrapper = useWrapper;
    }

    public boolean isMakeExecutable() {
        return makeExecutable;
    }

    @DataBoundSetter
    public void setMakeExecutable(boolean makeExecutable) {
        this.makeExecutable = makeExecutable;
    }

    public boolean isUseWorkspaceAsHome() {
        return useWorkspaceAsHome;
    }

    @DataBoundSetter
    public void setUseWorkspaceAsHome(boolean useWorkspaceAsHome) {
        this.useWorkspaceAsHome = useWorkspaceAsHome;
    }

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

    public String getProjectProperties() {
        return projectProperties;
    }

    @DataBoundSetter
    public void setProjectProperties(String projectProperties) {
        this.projectProperties = projectProperties;
    }

    /**
     * Sets the Java installation to use
     * @param jdkName the path of the jdk to use
     */
    @DataBoundSetter
    public void setJdkName (String jdkName) {
        this.jdkName = Util.fixEmpty(jdkName);
    }
    
    public String getJdkName () {
        return jdkName;
    }
    
    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new SynchronousNonBlockingStepExecution<Void>(context) {

            private static final long serialVersionUID = -8287401590137690657L;

            @Override
            protected Void run() throws Exception {
                this.getContext()
                    .get(FlowNode.class)
                    .addAction(
                        new LabelAction("Executing gradle build: " + tasks)
                    );
                
                EnvVars env = getContext().get(EnvVars.class);
                TaskListener listener = getContext().get(TaskListener.class);
                if (jdkName != null) {
                    JDK javaInstallation = Jenkins.getActiveInstance().getJDK(jdkName);
                    if (javaInstallation == null) {
                        listener.getLogger().printf("[Gradle] Java Installation '%s' not found. Defaulting to system installation. %n", jdkName);
                    } else {
                        listener.getLogger().printf("[Gradle] Java Installation found. Using '%s' %n", javaInstallation.getName());
                        env.put("JAVA_HOME", javaInstallation.getHome());
                    }
                }
                
                boolean success = new GradleExecution(
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
                    false, 
                    projectProperties, 
                    false,
                    GradleStep.this,
                    logEncoding
                ).performTask(
                    false, 
                    getContext().get(Run.class), 
                    getContext().get(Launcher.class),
                    listener,
                    getContext().get(FilePath.class),
                    getContext().get(FilePath.class),
                    new VariableResolver.ByMap<>(env),
                    env,
                    Collections.<String, String>emptyMap()
                );
                
                if (!success) {
                    throw new Exception("Gradle execution finished with failure. See log for further details.");
                }
                
                return null;
            }
        };
    }
    
    @Extension
    public static final class GradleStepDescriptor extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, FilePath.class, TaskListener.class, EnvVars.class);
        }

        @Override
        public String getFunctionName() {
            return "gradle";
        }

        @Override
        public String getDisplayName() {
            return "Executes a gradle build.";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return false;
        }

        /**
         * Obtains the {@link GradleInstallation.DescriptorImpl} instance.
         */
        public GradleInstallation[] getInstallations() {
            GradleInstallation.DescriptorImpl gradleDescriptor = ToolInstallation.all().get(GradleInstallation.DescriptorImpl.class);
            if (gradleDescriptor == null) {
                return new GradleInstallation[0];
            } else {
                return gradleDescriptor.getInstallations();
            }
        }

        /**
         * Obtains the {@link GradleInstallation.DescriptorImpl} instance.
         */
        public JDK[] getJdkInstallations() {
            JDK.DescriptorImpl jdkDescriptor = ToolInstallation.all().get(JDK.DescriptorImpl.class);
            if (jdkDescriptor == null) {
                return new JDK[0];
            } else {
                return jdkDescriptor.getInstallations();
            }
        }
    }
    
}
