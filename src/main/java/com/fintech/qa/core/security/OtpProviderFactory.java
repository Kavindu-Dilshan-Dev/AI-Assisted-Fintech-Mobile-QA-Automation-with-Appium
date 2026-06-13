package com.fintech.qa.core.security;

import com.fintech.qa.core.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Factory that selects the configured {@link OtpProvider} implementation.
 *
 * <p>The choice is driven by config key {@code otp.provider} (default {@code static}):</p>
 * <ul>
 *   <li>{@code static}  &rarr; {@link StaticOtpProvider}</li>
 *   <li>{@code testapi} &rarr; {@link TestApiOtpProvider}</li>
 * </ul>
 *
 * <p>An unrecognised value fails fast so misconfiguration is caught early rather than silently
 * falling back to a less secure mode.</p>
 */
public final class OtpProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(OtpProviderFactory.class);

    /** Config key selecting the provider. */
    private static final String OTP_PROVIDER_KEY = "otp.provider";

    /** Default provider when {@code otp.provider} is unset. */
    private static final String DEFAULT_PROVIDER = "static";

    private OtpProviderFactory() {
        throw new AssertionError("OtpProviderFactory is a static factory and must not be instantiated");
    }

    /**
     * Creates the {@link OtpProvider} selected by config key {@code otp.provider}.
     *
     * @return a new provider instance
     * @throws IllegalArgumentException if {@code otp.provider} holds an unsupported value
     */
    public static OtpProvider create() {
        String choice = ConfigManager.get(OTP_PROVIDER_KEY, DEFAULT_PROVIDER)
                .trim()
                .toLowerCase(Locale.ROOT);

        OtpProvider provider = switch (choice) {
            case "static" -> new StaticOtpProvider();
            case "testapi", "test-api", "test_api", "api" -> new TestApiOtpProvider();
            default -> throw new IllegalArgumentException(
                    "Unsupported otp.provider '" + choice + "'. Expected 'static' or 'testapi'.");
        };

        log.info("Selected OTP provider: {} ({})", provider.getClass().getSimpleName(), choice);
        return provider;
    }
}
