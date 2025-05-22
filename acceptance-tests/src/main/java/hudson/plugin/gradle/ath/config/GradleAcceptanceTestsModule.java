package hudson.plugin.gradle.ath.config;

import com.cloudbees.sdk.extensibility.Extension;
import com.cloudbees.sdk.extensibility.ExtensionModule;
import com.google.inject.AbstractModule;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Names;
import hudson.plugin.gradle.ath.updatecenter.VersionOverridesDecorator;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jenkinsci.test.acceptance.guice.TestScope;
import org.jenkinsci.test.acceptance.update_center.UpdateCenterMetadata;
import org.jenkinsci.test.acceptance.update_center.UpdateCenterMetadataProvider;
import org.openqa.selenium.WebDriver;

import java.io.File;
import java.util.function.Consumer;

/**
 * @see org.jenkinsci.test.acceptance.FallbackConfig#getWorkspace
 */
@Extension
public class GradleAcceptanceTestsModule extends AbstractModule implements ExtensionModule {

    @Override
    protected void configure() {
        bind(String.class)
            .annotatedWith(Names.named("WORKSPACE"))
            .toInstance(new File(System.getProperty("user.dir"), "target").getPath());

        bindInterceptor(
            Matchers.subclassesOf(UpdateCenterMetadataProvider.class),
            Matchers.returns(Matchers.subclassesOf(UpdateCenterMetadata.class)),
            new ResultDecoratingAdapter<>(new VersionOverridesDecorator())
        );
        bind(WebDriver.class).toProvider(WebDriverProvider.class).in(TestScope.class);
    }

    private static class ResultDecoratingAdapter<T> implements MethodInterceptor {

        private final Consumer<T> decorator;

        ResultDecoratingAdapter(Consumer<T> decorator) {
            this.decorator = decorator;
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Object result = invocation.proceed();
            if (result != null) {
                decorator.accept((T) result);
            }
            return result;
        }
    }
}
