package hudson.plugins.gradle;

import org.apache.commons.lang3.StringUtils;
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
import static org.junit.Assume.assumeTrue;

@Retention(RUNTIME)
@Target({METHOD, TYPE})
@Inherited
@Documented
@RuleAnnotation(value = WithEnvVariable.RuleImpl.class, priority = -10) // run before jenkins startup
public @interface WithEnvVariable {

    String value();

    class RuleImpl implements TestRule {

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {

                @Override
                public void evaluate() throws Throwable {
                    check(description.getAnnotation(WithEnvVariable.class));
                    check(description.getTestClass().getAnnotation(WithEnvVariable.class));

                    base.evaluate();
                }

                private void check(WithEnvVariable annotation) {
                    if (annotation == null) {
                        return;
                    }

                    String envVariable = annotation.value();
                    if (StringUtils.isBlank(envVariable)) {
                        return;
                    }

                    boolean isSet = System.getenv(envVariable) != null;
                    assumeTrue(
                        String.format("Missing required environment variable '%s'", envVariable), isSet);
                }
            };
        }
    }
}
