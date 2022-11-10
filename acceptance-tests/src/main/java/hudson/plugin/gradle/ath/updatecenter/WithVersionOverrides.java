package hudson.plugin.gradle.ath.updatecenter;

import org.jenkinsci.test.acceptance.junit.RuleAnnotation;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({METHOD, TYPE})
@Inherited
@Documented
@RuleAnnotation(value = WithVersionOverrides.RuleImpl.class)
public @interface WithVersionOverrides {

    String PLUGIN_VERSION_OVERRIDES = "hudson.plugin.gradle.pluginVersionOverrides";

    String value();

    class RuleImpl implements TestRule {

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {

                @Override
                public void evaluate() throws Throwable {
                    System.setProperty(
                        PLUGIN_VERSION_OVERRIDES,
                        description.getAnnotation(WithVersionOverrides.class).value()
                    );

                    base.evaluate();
                }
            };
        }
    }
}
