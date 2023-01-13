package hudson.plugin.gradle;

import org.jenkinsci.test.acceptance.po.Jenkins;
import org.jenkinsci.test.acceptance.po.JenkinsConfig;
import org.openqa.selenium.By;

import java.net.URI;

public class BuildScansInjectionSettings extends JenkinsConfig {

    private static final String XPATH = "//*[@path='%s']";

    private static final String INJECTION_CONFIG_PATH = "/hudson-plugins-gradle-config-GlobalConfig/";

    private static final String SERVER_FIELD = "server";
    private static final String GE_PLUGIN_VERSION_FIELD = "gradlePluginVersion";

    public BuildScansInjectionSettings(Jenkins jenkins) {
        super(jenkins);
    }

    public void clickBuildScansInjection() {
        ensureConfigPage();

        control(by.checkbox("Enable auto-injection")).click();
    }

    public void setGradleEnterpriseServerUrl(URI server) {
        setBuildScansInjectionFormValue(SERVER_FIELD, server.toString());
    }

    public void setGradleEnterprisePluginVersion(String version) {
        setBuildScansInjectionFormValue(GE_PLUGIN_VERSION_FIELD, version);
    }

    public void clickInjectMavenExtension() {
        ensureConfigPage();

        control(by.checkbox("Enable Gradle Enterprise Maven extension auto-injection")).click();
    }

    private void setBuildScansInjectionFormValue(String field, String value) {
        ensureConfigPage();

        By xpath = By.xpath(String.format(XPATH, INJECTION_CONFIG_PATH + field));
        driver.findElement(xpath).sendKeys(value);
    }
}
