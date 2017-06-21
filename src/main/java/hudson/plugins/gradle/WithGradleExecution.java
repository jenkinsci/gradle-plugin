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

import hudson.FilePath;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.*;

import javax.annotation.Nonnull;

/**
 * @author Alex Johnson
 */
public class WithGradleExecution extends StepExecution {

    private WithGradle step;
    private BodyExecution block;

    private FilePath workspace;
    private Run run;
    private TaskListener listener;

    public WithGradleExecution (StepContext context, WithGradle step) throws Exception { // TODO: do better
        super(context);
        this.step = step;

        workspace = context.get(FilePath.class);
        run = context.get(Run.class);
        listener = context.get(TaskListener.class);
    }

    @Override
    public boolean start() throws Exception {

        //ConsoleLogFilter annotator = BodyInvoker.mergeConsoleLogFilters(null, getContext().get(ConsoleLogFilter.class));
        //EnvironmentExpander expander = EnvironmentExpander.merge(null, getContext().get(EnvironmentExpander.class));

        block = getContext().newBodyInvoker().withContexts(workspace).start();
        getContext().onSuccess(Result.SUCCESS);
        return false;
    }

    @Override
    public void stop(@Nonnull Throwable cause) throws Exception {

    }

}
