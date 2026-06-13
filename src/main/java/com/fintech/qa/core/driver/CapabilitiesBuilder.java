package com.fintech.qa.core.driver;

import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.ios.options.XCUITestOptions;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fintech.qa.core.config.ConfigManager;

/**
 * Builds Appium driver options (capabilities) for each supported platform.
 *
 * <p>All values are read from {@link ConfigManager} so that non-secret defaults come from
 * {@code config.properties} and can be overridden by system properties / environment variables.
 * Biometric and enrollment-friendly flags are enabled so {@code BiometricHelper} can drive
 * fingerprint / Face ID simulations at runtime.</p>
 *
 * <p>Capability config keys (with defaults applied here):</p>
 * <ul>
 *   <li>{@code android.deviceName}, {@code android.platformVersion}, {@code android.automationName}</li>
 *   <li>{@code ios.deviceName}, {@code ios.platformVersion}, {@code ios.automationName}</li>
 *   <li>{@code app.package}, {@code app.activity}, {@code app.bundleId}, {@code app.path}</li>
 *   <li>{@code appium.newCommandTimeout}, {@code appium.autoGrantPermissions}, {@code appium.noReset}, {@code appium.fullReset}</li>
 * </ul>
 */
public final class CapabilitiesBuilder {

    private static final Logger log = LoggerFactory.getLogger(CapabilitiesBuilder.class);

    private CapabilitiesBuilder() {
        // Utility class; not instantiable.
    }

    /**
     * Builds Android (UiAutomator2) options from configuration.
     *
     * @return a populated {@link UiAutomator2Options}, biometric/enrollment friendly
     */
    public static UiAutomator2Options androidOptions() {
        UiAutomator2Options options = new UiAutomator2Options();

        options.setPlatformName("Android");
        options.setAutomationName(ConfigManager.get("android.automationName", "UiAutomator2"));
        options.setDeviceName(ConfigManager.get("android.deviceName", "Android Emulator"));

        String platformVersion = ConfigManager.get("android.platformVersion");
        if (platformVersion != null) {
            options.setPlatformVersion(platformVersion);
        }

        // App selection: prefer an artifact path; otherwise drive an installed package/activity.
        String appPath = ConfigManager.get("app.path");
        if (appPath != null && !appPath.isBlank()) {
            options.setApp(appPath);
        } else {
            String appPackage = ConfigManager.get("app.package");
            String appActivity = ConfigManager.get("app.activity");
            if (appPackage != null) {
                options.setAppPackage(appPackage);
            }
            if (appActivity != null) {
                options.setAppActivity(appActivity);
            }
        }

        // Session behaviour.
        options.setNewCommandTimeout(
                Duration.ofSeconds(ConfigManager.getInt("appium.newCommandTimeout", 120)));
        options.setAutoGrantPermissions(
                ConfigManager.getBoolean("appium.autoGrantPermissions", true));
        options.setNoReset(ConfigManager.getBoolean("appium.noReset", false));
        options.setFullReset(ConfigManager.getBoolean("appium.fullReset", false));

        // Webview support (hybrid screens such as SSL-pinning / 3DS flows).
        options.setEnsureWebviewsHavePages(true);
        options.setNativeWebScreenshot(true);

        // Stability / biometric-friendly flags. Animations off => deterministic waits.
        options.setCapability("appium:disableWindowAnimation", true);
        options.setCapability("appium:skipDeviceInitialization", false);
        // Keep the emulator biometric-enrollment friendly for BiometricHelper 'mobile: fingerprint'.
        options.setCapability("appium:enableMultiWindows", true);

        log.info("Built Android UiAutomator2 options: device='{}', automation='{}'.",
                options.getDeviceName().orElse("?"),
                options.getAutomationName().orElse("?"));
        return options;
    }

    /**
     * Builds iOS (XCUITest) options from configuration.
     *
     * @return a populated {@link XCUITestOptions}, biometric/enrollment friendly
     */
    public static XCUITestOptions iosOptions() {
        XCUITestOptions options = new XCUITestOptions();

        options.setPlatformName("iOS");
        options.setAutomationName(ConfigManager.get("ios.automationName", "XCUITest"));
        options.setDeviceName(ConfigManager.get("ios.deviceName", "iPhone 15"));

        String platformVersion = ConfigManager.get("ios.platformVersion");
        if (platformVersion != null) {
            options.setPlatformVersion(platformVersion);
        }

        // App selection: prefer an artifact path; otherwise drive an installed bundle id.
        String appPath = ConfigManager.get("app.path");
        if (appPath != null && !appPath.isBlank()) {
            options.setApp(appPath);
        } else {
            String bundleId = ConfigManager.get("app.bundleId");
            if (bundleId != null) {
                options.setBundleId(bundleId);
            }
        }

        // Session behaviour.
        options.setNewCommandTimeout(
                Duration.ofSeconds(ConfigManager.getInt("appium.newCommandTimeout", 120)));
        options.setNoReset(ConfigManager.getBoolean("appium.noReset", false));
        options.setFullReset(ConfigManager.getBoolean("appium.fullReset", false));
        // Leave alert handling to explicit security/biometric steps.
        options.setAutoAcceptAlerts(ConfigManager.getBoolean("appium.autoAcceptAlerts", false));

        // Keep the simulator biometric-enrollment friendly for BiometricHelper
        // 'mobile: enrollBiometric' / 'mobile: sendBiometricMatch'.
        options.setCapability("appium:allowTouchIdEnroll", true);
        options.setCapability("appium:connectHardwareKeyboard", false);
        options.setWdaLaunchTimeout(
                Duration.ofMillis(ConfigManager.getInt("ios.wdaLaunchTimeout", 120_000)));
        options.setWdaConnectionTimeout(
                Duration.ofMillis(ConfigManager.getInt("ios.wdaConnectionTimeout", 120_000)));

        log.info("Built iOS XCUITest options: device='{}', automation='{}'.",
                options.getDeviceName().orElse("?"),
                options.getAutomationName().orElse("?"));
        return options;
    }
}
