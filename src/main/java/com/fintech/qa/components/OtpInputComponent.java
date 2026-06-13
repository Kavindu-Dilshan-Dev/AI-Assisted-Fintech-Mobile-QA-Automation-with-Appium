package com.fintech.qa.components;

import com.fintech.qa.core.base.BasePage;
import io.appium.java_client.pagefactory.AndroidFindBy;
import io.appium.java_client.pagefactory.iOSXCUITFindBy;
import org.openqa.selenium.WebElement;

/**
 * One-time-password entry component.
 *
 * <p>Wraps the OTP input field of the verification screen. The OTP value is
 * treated as a secret: it is never logged in clear text — entry is delegated to
 * {@link BasePage#typeText(WebElement, String)}, which masks the value via
 * {@code MaskingUtil} before any log line is emitted.</p>
 */
public class OtpInputComponent extends BasePage {

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/input_otp")
    @iOSXCUITFindBy(accessibility = "login-otp-field")
    private WebElement otpField;

    /**
     * Enters the supplied one-time password into the OTP field.
     *
     * <p>The raw code is never logged; only a masked representation is recorded.</p>
     *
     * @param otp the one-time password (sensitive)
     */
    public void enterOtp(String otp) {
        typeText(otpField, otp);
        log.info("Entered OTP code (masked)");
    }
}
