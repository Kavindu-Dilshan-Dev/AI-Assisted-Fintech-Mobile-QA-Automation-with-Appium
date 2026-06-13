package com.fintech.qa.core.driver;

import com.fintech.qa.core.config.ConfigManager;

import java.util.Locale;

/**
 * Supported mobile automation platforms for the synthetic fintech wallet framework.
 *
 * <p>The active platform is resolved from configuration (key {@code platform}, default
 * {@code android}) via {@link #current()}, so the same suite can target Android or iOS
 * by changing {@code config.properties}, a {@code -Dplatform=ios} system property, or the
 * {@code PLATFORM} environment variable.</p>
 */
public enum Platform {

    /** Google Android (driven by the UiAutomator2 automation engine). */
    ANDROID,

    /** Apple iOS (driven by the XCUITest automation engine). */
    IOS;

    /** Config key selecting the target platform. */
    private static final String PLATFORM_KEY = "platform";

    /** Default platform when {@code platform} is unset. */
    private static final String DEFAULT_PLATFORM = "android";

    /**
     * Resolves the currently configured platform.
     *
     * <p>Reads {@link ConfigManager#get(String, String)} for key {@code platform}
     * (default {@code android}) and maps it case-insensitively to an enum constant.
     * Recognises common aliases ({@code ios}/{@code iphone}/{@code ipad} for {@link #IOS},
     * {@code android}/{@code droid} for {@link #ANDROID}).</p>
     *
     * @return the configured {@link Platform}
     * @throws IllegalStateException if the configured value is not a recognised platform
     */
    public static Platform current() {
        String value = ConfigManager.get(PLATFORM_KEY, DEFAULT_PLATFORM)
                .trim()
                .toLowerCase(Locale.ROOT);
        return switch (value) {
            case "android", "droid" -> ANDROID;
            case "ios", "iphone", "ipad" -> IOS;
            default -> throw new IllegalStateException(
                    "Unsupported platform '" + value + "'. Expected 'android' or 'ios'.");
        };
    }
}
