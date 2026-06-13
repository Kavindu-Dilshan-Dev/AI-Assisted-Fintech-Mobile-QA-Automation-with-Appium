package com.fintech.qa.components;

import com.fintech.qa.core.base.BasePage;
import com.fintech.qa.core.security.BiometricHelper;
import io.appium.java_client.pagefactory.AndroidFindBy;
import io.appium.java_client.pagefactory.iOSXCUITFindBy;
import org.openqa.selenium.WebElement;

/**
 * Biometric authentication prompt (fingerprint on Android / Face ID on iOS).
 *
 * <p>Detection of the prompt is done against the app's biometric prompt UI,
 * while the actual match / non-match is simulated through
 * {@link BiometricHelper}, which dispatches the platform-appropriate Appium
 * command (Android {@code mobile: fingerprint}, iOS
 * {@code mobile: sendBiometricMatch}). No real biometric hardware is touched.</p>
 */
public class BiometricPromptComponent extends BasePage {

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/biometric_prompt")
    @iOSXCUITFindBy(accessibility = "login-biometric-prompt")
    private WebElement biometricPrompt;

    /**
     * Reports whether the biometric prompt is currently shown.
     *
     * @return {@code true} if the prompt is displayed
     */
    public boolean isShown() {
        boolean shown = isDisplayed(biometricPrompt);
        log.info("Biometric prompt shown: {}", shown);
        return shown;
    }

    /**
     * Approves authentication by simulating a matching biometric.
     *
     * <p>Delegates to {@link BiometricHelper#match()}, which issues the
     * platform-specific success command.</p>
     */
    public void approveWithMatch() {
        log.info("Approving biometric prompt with a matching biometric (simulated)");
        BiometricHelper.match();
    }

    /**
     * Denies authentication by simulating a non-matching biometric.
     *
     * <p>Delegates to {@link BiometricHelper#nonMatch()}, used for security
     * negative tests (rejected biometric).</p>
     */
    public void denyWithNonMatch() {
        log.info("Denying biometric prompt with a non-matching biometric (simulated)");
        BiometricHelper.nonMatch();
    }
}
