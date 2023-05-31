package hudson.plugins.gradle.injection;

import hudson.Extension;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;
import org.jenkinsci.plugins.workflow.steps.DynamicContext;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;

@SuppressWarnings("unused")
@Extension
public class GradleEnterpriseExceptionTaskListenerDecoratorFactory extends DynamicContext.Typed<TaskListenerDecorator> {

    @Nonnull
    @Override
    protected Class<TaskListenerDecorator> type() {
        return TaskListenerDecorator.class;
    }

    @CheckForNull
    @Override
    protected TaskListenerDecorator get(DelegatedContext context) throws IOException, InterruptedException {
        Run run = context.get(Run.class);
        return new GradleEnterpriseExceptionTaskListenerDecorator(run);
    }

    @SuppressWarnings("rawtypes")
    public static class GradleEnterpriseExceptionTaskListenerDecorator extends TaskListenerDecorator {
        private static final long serialVersionUID = 1L;

        private final transient Run run;

        public GradleEnterpriseExceptionTaskListenerDecorator(Run run) {
            this.run = run;
        }

        @Nonnull
        @Override
        public OutputStream decorate(@Nonnull OutputStream logger) {
            InjectionConfig injectionConfig = InjectionConfig.get();
            if (injectionConfig.isEnabled() && injectionConfig.isCheckForBuildAgentErrors()) {
                return new GradleEnterpriseExceptionLogProcessor(logger, run);
            }
            return logger;
        }
    }

}
