package com.fintech.qa.core.security;

import com.fintech.qa.core.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link OtpProvider} for local/dev runs that returns a fixed code from configuration.
 *
 * <p>The code is read from config key {@code otp.static} (default {@code 000000}). This provider
 * never contacts a backend and must only be used against a synthetic app configured to accept the
 * static code. It is selected when {@code otp.provider=static} (the default).</p>
 */
public final class StaticOtpProvider implements OtpProvider {

    private static final Logger log = LoggerFactory.getLogger(StaticOtpProvider.class);

    /** Config key holding the fixed code. */
    private static final String OTP_STATIC_KEY = "otp.static";

    /** Default fixed code when {@code otp.static} is not configured. */
    private static final String DEFAULT_OTP = "000000";

    /**
     * Returns the configured static OTP, ignoring the user id.
     *
     * @param userId the user id (ignored by this provider)
     * @return the fixed OTP from config key {@code otp.static}, or {@code 000000} by default
     */
    @Override
    public String fetchOtp(String userId) {
        String otp = ConfigManager.get(OTP_STATIC_KEY, DEFAULT_OTP);
        // Logged value is masked; even a static dev code is treated as sensitive.
        log.debug("Static OTP provider returning configured code: {}", MaskingUtil.mask("otp: " + otp));
        return otp;
    }
}
