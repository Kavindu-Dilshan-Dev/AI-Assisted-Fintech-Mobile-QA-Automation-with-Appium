package com.fintech.qa.core.security;

import com.fintech.qa.core.driver.DriverManager;
import com.fintech.qa.core.driver.Platform;
import io.appium.java_client.AppiumDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Platform-aware helper for simulating biometric authentication in automated tests.
 *
 * <p>Biometrics are always simulated via Appium mobile commands; no real fingerprint or face data is
 * ever used. The active {@link Platform} is read from {@link DriverManager#getPlatform()}:</p>
 * <ul>
 *   <li><b>Android</b> &rarr; {@code mobile: fingerprint} with {@code fingerprintId=1}. A non-match
 *       is simulated with a different, unenrolled {@code fingerprintId}.</li>
 *   <li><b>iOS</b> &rarr; {@code mobile: sendBiometricMatch} with {@code type=faceId} and
 *       {@code match=true|false}. Enrollment uses {@code mobile: enrollBiometric}.</li>
 * </ul>
 *
 * <p>All methods are static; the class cannot be instantiated.</p>
 */
public final class BiometricHelper {

    private static final Logger log = LoggerFactory.getLogger(BiometricHelper.class);

    /** Enrolled fingerprint id used for a successful Android match. */
    private static final int ENROLLED_FINGERPRINT_ID = 1;

    /** Unenrolled fingerprint id used to simulate an Android non-match. */
    private static final int UNENROLLED_FINGERPRINT_ID = 2;

    private BiometricHelper() {
        throw new AssertionError("BiometricHelper is a utility class and must not be instantiated");
    }

    /**
     * Enrolls biometrics on the device/emulator so subsequent matches can succeed.
     *
     * <p>On Android the platform fingerprint subsystem is enrolled implicitly by issuing a
     * fingerprint event against the enrolled id; on iOS biometric enrollment is toggled on.</p>
     */
    public static void enroll() {
        Platform platform = DriverManager.getPlatform();
        AppiumDriver driver = DriverManager.getDriver();
        log.info("Enrolling biometric authentication for platform {}", platform);
        switch (platform) {
            case ANDROID ->
                // Emitting an event against the enrolled id primes the emulator's fingerprint store.
                    driver.executeScript("mobile: fingerprint",
                            Map.of("fingerprintId", ENROLLED_FINGERPRINT_ID));
            case IOS ->
                    driver.executeScript("mobile: enrollBiometric", Map.of("isEnabled", true));
            default -> throw new IllegalStateException("Unsupported platform: " + platform);
        }
    }

    /**
     * Simulates a successful biometric authentication (matching fingerprint / Face ID).
     */
    public static void match() {
        Platform platform = DriverManager.getPlatform();
        AppiumDriver driver = DriverManager.getDriver();
        log.info("Simulating successful biometric match for platform {}", platform);
        switch (platform) {
            case ANDROID ->
                    driver.executeScript("mobile: fingerprint",
                            Map.of("fingerprintId", ENROLLED_FINGERPRINT_ID));
            case IOS ->
                    driver.executeScript("mobile: sendBiometricMatch",
                            Map.of("type", "faceId", "match", true));
            default -> throw new IllegalStateException("Unsupported platform: " + platform);
        }
    }

    /**
     * Simulates a failed biometric authentication (non-matching fingerprint / Face ID).
     */
    public static void nonMatch() {
        Platform platform = DriverManager.getPlatform();
        AppiumDriver driver = DriverManager.getDriver();
        log.info("Simulating failed biometric (non-match) for platform {}", platform);
        switch (platform) {
            case ANDROID ->
                // An unenrolled fingerprint id is rejected by the platform, yielding a non-match.
                    driver.executeScript("mobile: fingerprint",
                            Map.of("fingerprintId", UNENROLLED_FINGERPRINT_ID));
            case IOS ->
                    driver.executeScript("mobile: sendBiometricMatch",
                            Map.of("type", "faceId", "match", false));
            default -> throw new IllegalStateException("Unsupported platform: " + platform);
        }
    }
}
