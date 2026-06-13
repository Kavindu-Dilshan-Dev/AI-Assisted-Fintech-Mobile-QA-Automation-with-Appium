package com.fintech.qa.core.driver;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fintech.qa.core.config.ConfigManager;

/**
 * Creates platform-specific {@link AppiumDriver} sessions.
 *
 * <p>The factory resolves the target {@link Platform}, builds the matching options via
 * {@link CapabilitiesBuilder}, and connects to the Appium server configured by
 * {@code appium.server.url} (default {@code http://127.0.0.1:4723}).</p>
 *
 * <p>The returned driver is <em>not</em> registered with {@link DriverManager}; the caller
 * (typically {@code CucumberHooks}) is responsible for {@link DriverManager#setDriver(AppiumDriver)}
 * so that thread confinement remains explicit.</p>
 */
public final class DriverFactory {

    private static final Logger log = LoggerFactory.getLogger(DriverFactory.class);

    private static final String DEFAULT_SERVER_URL = "http://127.0.0.1:4723";

    private DriverFactory() {
        // Utility class; not instantiable.
    }

    /**
     * Creates a new {@link AppiumDriver} for the currently configured platform.
     *
     * @return an {@link AndroidDriver} or {@link IOSDriver} per {@link Platform#current()}
     * @throws IllegalStateException if the Appium server URL is malformed or the session cannot start
     */
    public static AppiumDriver createDriver() {
        Platform platform = Platform.current();
        URL serverUrl = resolveServerUrl();

        log.info("Creating {} session against Appium server '{}'.", platform, serverUrl);

        try {
            AppiumDriver driver = switch (platform) {
                case ANDROID -> new AndroidDriver(serverUrl, CapabilitiesBuilder.androidOptions());
                case IOS -> new IOSDriver(serverUrl, CapabilitiesBuilder.iosOptions());
            };
            log.info("{} session established (sessionId={}).", platform, driver.getSessionId());
            return driver;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to create " + platform + " AppiumDriver against '" + serverUrl + "': " + e.getMessage(), e);
        }
    }

    /**
     * Resolves the Appium server URL from configuration, building it via
     * {@code URI.create(url).toURL()} as mandated by the framework contract.
     */
    private static URL resolveServerUrl() {
        String url = ConfigManager.get("appium.server.url", DEFAULT_SERVER_URL);
        try {
            return URI.create(url).toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid Appium server URL '" + url + "': " + e.getMessage(), e);
        }
    }
}
