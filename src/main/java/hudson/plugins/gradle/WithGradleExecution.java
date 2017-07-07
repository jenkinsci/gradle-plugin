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

import hudson.EnvVars;
import hudson.FilePath;
import hudson.console.ConsoleLogFilter;
import hudson.model.*;
import hudson.tools.ToolInstallation;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.*;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;

/**
 * The execution of the {@link WithGradle} pipeline step. Configures the ConsoleAnnotator for a Gradle build and, if
 * specified, configures Gradle and Java for the build.
 *
 * @author Alex Johnson
 */
public class WithGradleExecution extends StepExecution {

    /** The step for the Execution */
    private transient WithGradle step;
    private BodyExecution block;

    public WithGradleExecution(StepContext context, WithGradle step) throws IOException, InterruptedException {
        super(context);
        this.step = step;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean start() throws Exception {
        TaskListener listener = getContext().get(TaskListener.class);
        EnvVars envVars = getContext().get(EnvVars.class);

        listener.getLogger().printf("[WithGradle] Execution begin %n");
        String gradleName = step.getGradle();
        if (gradleName != null) {
            GradleInstallation gradleInstallation = null;
            GradleInstallation[] installations = ToolInstallation.all().get(GradleInstallation.DescriptorImpl.class).getInstallations();
            for (GradleInstallation i : installations) {
                if (i.getName().equals(gradleName)) {
                    gradleInstallation = i;
                }
            }
            if (gradleInstallation == null) {
                listener.getLogger().printf("[WithGradle] Gradle Installation '%s' not found. Defaulting to system installation. %n", gradleName);
            } else {
                listener.getLogger().printf("[WithGradle] Gradle Installation found. Using '%s' %n", gradleInstallation.getName());
                envVars.put("GRADLE_HOME", gradleInstallation.getHome());
            }
        } else {
            listener.getLogger().printf("[WithGradle] Defaulting to system installation of Gradle. %n");
        }

        String javaName = step.getJdk();
        if (javaName != null) {
            JDK javaInstallation = Jenkins.getActiveInstance().getJDK(javaName);
            if (javaInstallation == null) {
                listener.getLogger().printf("[WithGradle] Java Installation '%s' not found. Defaulting to system installation. %n", javaName);
            } else {
                listener.getLogger().printf("[WithGradle] Java Installation found. Using '%s' %n", javaInstallation.getName());
                envVars.put("JAVA_HOME", javaInstallation.getHome());
            }
        }

        ConsoleLogFilter annotator = BodyInvoker.mergeConsoleLogFilters(getContext().get(ConsoleLogFilter.class), new GradleConsoleFilter());
        EnvironmentExpander expander = EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), EnvironmentExpander.constant(envVars));

        block = getContext().newBodyInvoker().withCallback(BodyExecutionCallback.wrap(getContext())).withContexts(annotator, expander).start();

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop(@Nonnull Throwable cause) throws Exception {

    }

    /**
     * Wraps {@link GradleConsoleAnnotator} in a {@link ConsoleLogFilter} so it can be merged with the existing
     * log filter.
     */
    private static class GradleConsoleFilter extends ConsoleLogFilter implements Serializable {

        private static final long serialVersionUID = 1;

        public GradleConsoleFilter() {
        }

        /**
         * Creates a {@link GradleConsoleAnnotator} for an {@link OutputStream}
         *
         * @param run this is ignored
         * @param out the {@link OutputStream} to annotate
         * @return the {@link GradleConsoleAnnotator} for the OutputStream
         */
        @Override
        public OutputStream decorateLogger(Run run, final OutputStream out) {
            return new GradleConsoleAnnotator(out, Charset.forName("UTF-8"));
        }
    }
}
