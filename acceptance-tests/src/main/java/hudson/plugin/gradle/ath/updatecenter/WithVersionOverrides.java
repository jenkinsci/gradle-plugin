package hudson.plugin.gradle.ath.updatecenter;

import org.jenkinsci.test.acceptance.junit.RuleAnnotation;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({METHOD})
@Inherited
@Documented
@RuleAnnotation(value = WithVersionOverrides.RuleImpl.class, priority = -10)
public @interface WithVersionOverrides {

    String PLUGIN_VERSION_OVERRIDES = "hudson.plugin.gradle.pluginVersionOverrides";

    String value();

    class RuleImpl implements TestRule {

        private static final Logger LOGGER = Logger.getLogger(RuleImpl.class.getName());

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {

                @Override
                public void evaluate() throws Throwable {
                    String overrides = description.getAnnotation(WithVersionOverrides.class).value();

                    System.setProperty(PLUGIN_VERSION_OVERRIDES, overrides);
                    LOGGER.log(Level.INFO, "Plugin version overrides: {0}", overrides);

                    try {
                        base.evaluate();
                    } finally {
                        System.clearProperty(PLUGIN_VERSION_OVERRIDES);
                    }
                }
            };
        }
    }
}
