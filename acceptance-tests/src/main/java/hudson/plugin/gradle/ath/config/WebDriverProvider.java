package hudson.plugin.gradle.ath.config;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.jenkinsci.test.acceptance.FallbackConfig;
import org.jenkinsci.test.acceptance.guice.TestCleaner;
import org.jenkinsci.test.acceptance.guice.TestName;
import org.jenkinsci.test.acceptance.selenium.Scroller;
import org.jenkinsci.test.acceptance.utils.ElasticTime;
import org.junit.runners.model.Statement;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.UnsupportedCommandException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.events.EventFiringWebDriver;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.logging.Logger;

class WebDriverProvider implements Provider<WebDriver> {

    private static final Logger LOGGER = Logger.getLogger(FallbackConfig.class.getName());
    private final TestCleaner testCleaner;
    private final FallbackConfig fallbackConfig;
    private final TestName testName;
    private final ElasticTime time;

    @Inject
    public WebDriverProvider(
        TestCleaner testCleaner,
        FallbackConfig fallbackConfig,
        TestName testName,
        ElasticTime time) {
        this.testCleaner = testCleaner;
        this.fallbackConfig = fallbackConfig;
        this.testName = testName;
        this.time = time;
    }

    private String getBrowser() {
        String browser = System.getenv("BROWSER");
        if (browser == null) browser = "chrome-container";
        browser = browser.toLowerCase(Locale.ENGLISH);
        return browser;
    }

    @Override
    public WebDriver get() {
        if ("chrome".equals(getBrowser())) {
            return createChromeWebDriver();
        }
        try {
            return fallbackConfig.createWebDriver(testCleaner, testName, time);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private WebDriver createChromeWebDriver() {
        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.setBrowserVersion("stable");
        chromeOptions.addArguments("--window-position=0,0");
        chromeOptions.addArguments("--window-size=1280,720");
        chromeOptions.addArguments("--lang=en_US");
        chromeOptions.setExperimentalOption("prefs", ImmutableMap.of("intl.accept_languages", "en_US"));
        ChromeDriver d = new ChromeDriver(chromeOptions);
        Dimension oldSize = d.manage().window().getSize();
        if (oldSize.height < 1050 || oldSize.width < 1680) {
            d.manage().window().setSize(new Dimension(1680, 1050));
        }
        final EventFiringWebDriver driver = new EventFiringWebDriver(d);
        driver.register(new Scroller());
        try {
            driver.manage().timeouts().pageLoadTimeout(Duration.ofMillis(time.seconds(FallbackConfig.PAGE_LOAD_TIMEOUT)));
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(time.seconds(FallbackConfig.IMPLICIT_WAIT_TIMEOUT)));
        } catch (UnsupportedCommandException e) {
            // sauce labs RemoteWebDriver doesn't support this
            LOGGER.info(d + " doesn't support page load timeout");
        }
        testCleaner.addTask(new Statement() {
            @Override
            public void evaluate() {
                driver.quit();
            }

            @Override
            public String toString() {
                return "Close WebDriver after test";
            }
        });
        return driver;
    }
}
