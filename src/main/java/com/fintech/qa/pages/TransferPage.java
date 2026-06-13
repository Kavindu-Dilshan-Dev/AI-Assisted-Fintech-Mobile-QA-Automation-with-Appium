package com.fintech.qa.pages;

import com.fintech.qa.components.OtpInputComponent;
import com.fintech.qa.components.ToastComponent;
import com.fintech.qa.core.base.BasePage;
import com.fintech.qa.core.security.MaskingUtil;
import com.fintech.qa.core.security.OtpProvider;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.pagefactory.AndroidFindBy;
import io.appium.java_client.pagefactory.iOSXCUITFindBy;
import org.openqa.selenium.WebElement;

/**
 * Money-transfer screen of the synthetic fintech wallet.
 *
 * <p>Drives the transfer flow used by the scenarios: select a (synthetic) beneficiary, enter an
 * amount, and confirm with an OTP obtained from an {@link OtpProvider} (never a real SMS). The
 * fluent setters return {@code this} so steps can chain naturally; {@link #confirmWithOtp(OtpProvider)}
 * and {@link #isTransferSuccessful()} report the outcome.</p>
 *
 * <p>Locators are declared as {@code @AndroidFindBy}/{@code @iOSXCUITFindBy} annotations and mirror
 * {@code src/test/resources/locators/transfer-<platform>.json}. Per the framework rule, element
 * lookups are performed exclusively by {@link BasePage}; beneficiary selection uses a dynamic
 * accessibility-id locator resolved through the base {@code waitForVisible(By)} helper.</p>
 */
public class TransferPage extends BasePage {

    /** A widget unique to the transfer screen, used by {@link #isLoaded()}. */
    @AndroidFindBy(id = "com.fintech.wallet.sample:id/transfer_root")
    @iOSXCUITFindBy(accessibility = "transfer-root")
    private WebElement transferRoot;

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/input_amount")
    @iOSXCUITFindBy(accessibility = "transfer-amount-field")
    private WebElement amountField;

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/btn_confirm_transfer")
    @iOSXCUITFindBy(accessibility = "transfer-confirm-button")
    private WebElement confirmButton;

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/transfer_success")
    @iOSXCUITFindBy(accessibility = "transfer-success")
    private WebElement successIndicator;

    /** Composed component for entering the OTP that authorises the transfer. */
    private final OtpInputComponent otpInput = new OtpInputComponent();

    /** Composed component for reading the confirmation toast/snackbar. */
    private final ToastComponent toast = new ToastComponent();

    /**
     * @return {@code true} when the transfer screen is rendered (its root element is displayed)
     */
    public boolean isLoaded() {
        boolean loaded = isDisplayed(transferRoot);
        log.debug("TransferPage.isLoaded() = {}", loaded);
        return loaded;
    }

    /**
     * Selects a beneficiary by display name from the beneficiary list.
     *
     * <p>The beneficiary row is addressed dynamically by its accessibility id
     * ({@code beneficiary-<name>}), resolved via the {@link BasePage} wait/lookup helpers so no
     * raw {@code findElement} happens outside the base class.</p>
     *
     * @param name the (synthetic) beneficiary display name
     * @return this page for fluent chaining
     */
    public TransferPage selectBeneficiary(String name) {
        log.info("Selecting beneficiary on transfer screen");
        String accessibilityId = "beneficiary-" + (name == null ? "" : name.trim());
        WebElement row = waitForVisible(AppiumBy.accessibilityId(accessibilityId));
        tap(row);
        return this;
    }

    /**
     * Enters the transfer amount.
     *
     * @param amount the amount as a string (e.g. {@code "25.00"})
     * @return this page for fluent chaining
     */
    public TransferPage enterAmount(String amount) {
        log.info("Entering transfer amount: {}", MaskingUtil.mask(amount));
        typeText(amountField, amount);
        return this;
    }

    /**
     * Confirms the transfer, supplying the OTP fetched from the given provider.
     *
     * @param otp the OTP provider used to obtain the one-time confirmation code
     * @return {@code true} if the transfer reported success after confirmation
     */
    public boolean confirmWithOtp(OtpProvider otp) {
        log.info("Confirming transfer with OTP");
        tap(confirmButton);
        String code = otp.fetchOtp(null);
        otpInput.enterOtp(code);
        boolean success = isTransferSuccessful();
        log.info("Transfer confirmation result: {}", success);
        return success;
    }

    /**
     * Reports whether the transfer succeeded, by checking the success indicator or a confirmation
     * toast/snackbar.
     *
     * @return {@code true} if a success signal is present
     */
    public boolean isTransferSuccessful() {
        return isDisplayed(successIndicator) || toast.isShown();
    }
}
