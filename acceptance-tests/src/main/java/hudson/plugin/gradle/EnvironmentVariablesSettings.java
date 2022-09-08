package hudson.plugin.gradle;

import org.jenkinsci.test.acceptance.po.Jenkins;
import org.jenkinsci.test.acceptance.po.JenkinsConfig;
import org.openqa.selenium.By;

public class EnvironmentVariablesSettings extends JenkinsConfig {

    private static final String XPATH = "//*[@path='%s']";

    private static final String ENV_VAR_PATH = "/jenkins-model-GlobalNodePropertiesConfiguration/globalNodeProperties/hudson-slaves-EnvironmentVariablesNodeProperty/";
    private static final String BUTTON_ADD = "repeatable-add";
    private static final String KEY_FILED = "key";
    private static final String VALUE_FILED = "value";

    private int count;

    public EnvironmentVariablesSettings(Jenkins jenkins) {
        super(jenkins);
    }

    public void clickEnvironmentVariables() {
        ensureConfigPage();

        control(by.checkbox("Environment variables")).click();
    }

    public void addEnvironmentVariable(String key, String value) {
        ensureConfigPage();

        driver.findElement(By.xpath(String.format(XPATH, ENV_VAR_PATH + BUTTON_ADD))).click();

        String countPrefix = count == 0 ? "env/" : "env[" + count + "]/";
        driver.findElement(By.xpath(String.format(XPATH, ENV_VAR_PATH + countPrefix + KEY_FILED))).sendKeys(key);
        driver.findElement(By.xpath(String.format(XPATH, ENV_VAR_PATH + countPrefix + VALUE_FILED))).sendKeys(value);
        count++;
    }
}
