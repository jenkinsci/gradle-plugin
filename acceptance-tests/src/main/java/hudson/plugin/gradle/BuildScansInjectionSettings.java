package hudson.plugin.gradle;

import org.jenkinsci.test.acceptance.po.Jenkins;
import org.jenkinsci.test.acceptance.po.JenkinsConfig;
import org.openqa.selenium.By;

import java.net.URI;

public class BuildScansInjectionSettings extends JenkinsConfig {

    private static final String XPATH = "//*[@path='%s']";

    private static final String INJECTION_CONFIG_PATH = "/hudson-plugins-gradle-injection-InjectionConfig/";
    private static final String ENRICHED_CONFIG_PATH = "/hudson-plugins-gradle-enriched-EnrichedSummaryConfig/";

    private static final String SERVER_FIELD = "server";
    private static final String BUILD_SCAN_SERVER_FIELD = "buildScanServer";
    private static final String GE_PLUGIN_VERSION_FIELD = "gradlePluginVersion";
    private static final String GIT_REPOSITORY_FILTERS_FIELD = "vcsRepositoryFilter";
    private static final String GE_ACCESS_KEY_FIELD = "accessKey";

    public BuildScansInjectionSettings(Jenkins jenkins) {
        super(jenkins);
    }

    public void clickBuildScansEnriched() {
        ensureConfigPage();

        control(by.checkbox("Enable enriched summary")).click();
    }

    public void setGradleEnterpriseBuildScanServerUrl(URI server) {
        setBuildScansEnrichedFormValue(BUILD_SCAN_SERVER_FIELD, server.toString());
    }

    public void clickBuildScansInjection() {
        ensureConfigPage();

        control(by.checkbox("Enable auto-injection")).click();
    }

    public void setGradleEnterpriseServerUrl(URI server) {
        setBuildScansInjectionFormValue(SERVER_FIELD, server.toString());
    }

    public void clickEnforceUrl() {
        ensureConfigPage();

        control(by.checkbox("Enforce Gradle Enterprise server url")).click();
    }

    public void clickAllowUntrustedServer() {
        ensureConfigPage();

        control(by.checkbox("Allow untrusted server")).click();
    }

    public void setGradleEnterpriseAccessKey(String accessKey) {
        setBuildScansInjectionFormValue(GE_ACCESS_KEY_FIELD, accessKey);
    }

    public void setGradleEnterprisePluginVersion(String version) {
        setBuildScansInjectionFormValue(GE_PLUGIN_VERSION_FIELD, version);
    }

    public void setGitRepositoryFilters(String filters) {
        setBuildScansInjectionFormValue(GIT_REPOSITORY_FILTERS_FIELD, filters);
    }

    public String getGitRepositoryFilters() {
        return getBuildScansInjectionFormValue(GIT_REPOSITORY_FILTERS_FIELD);
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

    private String getBuildScansInjectionFormValue(String field) {
        ensureConfigPage();

        By xpath = By.xpath(String.format(XPATH, INJECTION_CONFIG_PATH + field));
        return driver.findElement(xpath).getText();
    }

    private void setBuildScansEnrichedFormValue(String field, String value) {
        ensureConfigPage();

        By xpath = By.xpath(String.format(XPATH, ENRICHED_CONFIG_PATH + field));
        driver.findElement(xpath).sendKeys(value);
    }
}
