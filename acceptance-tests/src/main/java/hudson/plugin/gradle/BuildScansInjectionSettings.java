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
    private static final String DEVELOCITY_PLUGIN_VERSION_FIELD = "gradlePluginVersion";
    private static final String DEVELOCITY_EXTENSION_VERSION_FIELD = "mavenExtensionVersion";
    private static final String GIT_REPOSITORY_FILTERS_FIELD = "vcsRepositoryFilter";
    private static final String DEVELOCITY_ACCESS_KEY_FIELD = "accessKey";
    private static final String DEVELOCITY_GRADLE_PLUGIN_REPOSITORY_PASSWORD_FIELD = "gradlePluginRepositoryPassword";

    public BuildScansInjectionSettings(Jenkins jenkins) {
        super(jenkins);
    }

    public void clickBuildScansEnriched() {
        clickCheckboxOnConfig("Enable enriched summary");
    }

    public void setGradleEnterpriseBuildScanServerUrl(URI server) {
        setBuildScansEnrichedFormValue(BUILD_SCAN_SERVER_FIELD, server.toString());
    }

    public void clickBuildScansInjection() {
        clickCheckboxOnConfig("Enable auto-injection");
    }

    public void setDevelocityServerUrl(URI server) {
        setBuildScansInjectionFormValue(SERVER_FIELD, server.toString());
    }

    public void clickEnforceUrl() {
        clickCheckboxOnConfig("Enforce Develocity server url");
    }

    public void clickAllowUntrustedServer() {
        clickCheckboxOnConfig("Allow untrusted server");
    }

    public void clickCheckForGradleEnterpriseErrors() {
        clickCheckboxOnConfig("Check for the Develocity build agent errors");
    }

    public void setGradleEnterpriseAccessKey(String accessKey) {
        setBuildScansInjectionFormValue(DEVELOCITY_ACCESS_KEY_FIELD, accessKey);
    }

    public void setGradleEnterpriseGradlePluginRepoPassword(String password) {
        setBuildScansInjectionFormValue(DEVELOCITY_GRADLE_PLUGIN_REPOSITORY_PASSWORD_FIELD, password);
    }

    public void setGradleEnterprisePluginVersion(String version) {
        setBuildScansInjectionFormValue(DEVELOCITY_PLUGIN_VERSION_FIELD, version);
    }

    public void setGitRepositoryFilters(String filters) {
        setBuildScansInjectionFormValue(GIT_REPOSITORY_FILTERS_FIELD, filters);
    }

    public String getGitRepositoryFilters() {
        return getBuildScansInjectionFormValue(GIT_REPOSITORY_FILTERS_FIELD);
    }

    public void setDevelocityMavenExtensionVersion(String version) {
        setBuildScansInjectionFormValue(DEVELOCITY_EXTENSION_VERSION_FIELD, version);
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

    private void clickCheckboxOnConfig(String locator) {
        ensureConfigPage();

        control(by.checkbox(locator)).click();
    }
}
