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

import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.collect.ImmutableSet;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.JDK;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tools.ToolInstallation;

/**
 * The WithGradle pipeline step. For the most part only getters, setters and scaffolding code. The actual logic and
 * configuration are in {@link WithGradleExecution}.
 *
 * @author Alex Johnson
 */
public class WithGradle extends Step {

    /** The Gradle installation to use */
    private String gradleName;
    /** The name of the Java Installation */
    private String jdkName;

    @DataBoundConstructor
    public WithGradle () {

    }

    /**
     * Sets the globally configured gradle installation to set the GRADLE_HOME for
     * @param gradleName the name of the installation to use
     */
    @DataBoundSetter
    public void setGradleName (String gradleName) {
        this.gradleName = Util.fixEmpty(gradleName);
    }

    public String getGradleName () {
        return gradleName;
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
        return new WithGradleExecution(context, this);
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, FilePath.class, TaskListener.class, EnvVars.class);
        }

        @Override
        public String getFunctionName() {
            return "withGradle";
        }

        @Override
        public String getDisplayName() {
            return "Sets up a Gradle environment and annotates the console output";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
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
