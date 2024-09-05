package hudson.plugin.gradle;

import org.jenkinsci.test.acceptance.plugins.credentials.CredentialsPage;
import org.jenkinsci.test.acceptance.plugins.credentials.ManagedCredentials;
import org.jenkinsci.test.acceptance.plugins.credentials.StringCredentials;
import org.jenkinsci.test.acceptance.plugins.credentials.UserPwdCredential;
import org.jenkinsci.test.acceptance.po.Jenkins;
import org.jenkinsci.test.acceptance.po.JenkinsConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import java.net.URI;
import java.util.UUID;

public class BuildScansInjectionSettings extends JenkinsConfig {

    private static final String XPATH = "//*[@path='%s']";

    private static final String INJECTION_CONFIG_PATH = "/hudson-plugins-gradle-injection-InjectionConfig/";
    private static final String ENRICHED_CONFIG_PATH = "/hudson-plugins-gradle-enriched-EnrichedSummaryConfig/";

    private static final String SERVER_FIELD = "server";
    private static final String BUILD_SCAN_SERVER_FIELD = "buildScanServer";
    private static final String DEVELOCITY_PLUGIN_VERSION_FIELD = "gradlePluginVersion";
    private static final String DEVELOCITY_EXTENSION_VERSION_FIELD = "mavenExtensionVersion";
    private static final String GIT_REPOSITORY_FILTERS_FIELD = "vcsRepositoryFilter";
    private static final String DEVELOCITY_ACCESS_KEY_CREDENTIAL_ID_FIELD = "accessKeyCredentialId";
    private static final String DEVELOCITY_GRADLE_PLUGIN_REPOSITORY_CREDENTIAL_ID_FIELD = "gradlePluginRepositoryCredentialId";

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
        JenkinsConfig configPage = jenkins.getConfigPage();
        configPage.configure();

        CredentialsPage credentialsPage = new CredentialsPage(jenkins, ManagedCredentials.DEFAULT_DOMAIN);
        credentialsPage.open();

        StringCredentials stringCredentials = credentialsPage.add(StringCredentials.class);
        stringCredentials.setId(UUID.randomUUID().toString());
        stringCredentials.secret.set(accessKey);

        String credentialDescription = "Gradle Plugin Repository Password";
        stringCredentials.description.set(credentialDescription);

        credentialsPage.create();

        setBuildScansInjectionFormSelect(DEVELOCITY_ACCESS_KEY_CREDENTIAL_ID_FIELD, credentialDescription);
    }

    public void setGradleEnterpriseGradlePluginRepoPassword(String password) {
        JenkinsConfig configPage = jenkins.getConfigPage();
        configPage.configure();

        CredentialsPage credentialsPage = new CredentialsPage(jenkins, ManagedCredentials.DEFAULT_DOMAIN);
        credentialsPage.open();

        UserPwdCredential usernamePasswordCredentials = credentialsPage.add(UserPwdCredential.class);
        usernamePasswordCredentials.setId(UUID.randomUUID().toString());
        usernamePasswordCredentials.username.set("johndoe");
        usernamePasswordCredentials.password.set(password);

        String credentialDescription = "Gradle Plugin Repository Password";
        usernamePasswordCredentials.description.set(credentialDescription);

        credentialsPage.create();

        setBuildScansInjectionFormSelect(DEVELOCITY_GRADLE_PLUGIN_REPOSITORY_CREDENTIAL_ID_FIELD, credentialDescription);
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

    private void setBuildScansInjectionFormSelect(String field, String value) {
        ensureConfigPage();

        By xpath = By.xpath(String.format(XPATH, INJECTION_CONFIG_PATH + field));
        WebElement webElement = driver.findElement(xpath);

        Select select = new Select(webElement);
        select.selectByVisibleText(value);
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
