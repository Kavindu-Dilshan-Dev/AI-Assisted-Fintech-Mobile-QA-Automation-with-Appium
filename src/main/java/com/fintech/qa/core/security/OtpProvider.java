package com.fintech.qa.core.security;

/**
 * Strategy for obtaining a one-time passcode (OTP) for a given user during automated tests.
 *
 * <p>Implementations MUST never read a real SMS or expose a real secret. The framework ships two
 * implementations:</p>
 * <ul>
 *   <li>{@link StaticOtpProvider} — returns a fixed local/dev code (default {@code 000000}).</li>
 *   <li>{@link TestApiOtpProvider} — fetches the code from a test mailbox/backend over HTTP, with
 *       the auth token supplied only via environment variables.</li>
 * </ul>
 *
 * <p>Use {@link OtpProviderFactory#create()} to obtain the configured implementation.</p>
 */
public interface OtpProvider {

    /**
     * Fetches the current OTP for the supplied user.
     *
     * @param userId the identifier (username, phone, or mailbox key) the OTP was issued for;
     *               may be ignored by providers that return a fixed code
     * @return the one-time passcode as a string of digits; never {@code null}
     */
    String fetchOtp(String userId);
}
