package com.fintech.qa.pages;

import com.fintech.qa.components.BiometricPromptComponent;
import com.fintech.qa.components.OtpInputComponent;
import com.fintech.qa.core.base.BasePage;
import com.fintech.qa.core.security.BiometricHelper;
import com.fintech.qa.core.security.OtpProvider;
import io.appium.java_client.pagefactory.AndroidFindBy;
import io.appium.java_client.pagefactory.iOSXCUITFindBy;
import org.openqa.selenium.WebElement;

/**
 * Login page of the synthetic fintech wallet.
 *
 * <p>Supports two authentication flows used by the scenarios:</p>
 * <ul>
 *   <li><b>Username/password + OTP</b> via {@link #loginWithOtp(String, String, OtpProvider)}.</li>
 *   <li><b>Biometric</b> via {@link #loginWithBiometric()} (simulated through
 *       {@link BiometricHelper}, never a real sensor).</li>
 * </ul>
 *
 * <p>Locators are declared as {@code @AndroidFindBy}/{@code @iOSXCUITFindBy} annotations and
 * mirror {@code src/test/resources/locators/login-<platform>.json}. Secrets (the password) are
 * never logged here in clear text; {@link BasePage#typeText(WebElement, String)} masks input.</p>
 */
public class LoginPage extends BasePage {

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/input_username")
    @iOSXCUITFindBy(accessibility = "login-username-field")
    private WebElement usernameField;

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/input_password")
    @iOSXCUITFindBy(accessibility = "login-password-field")
    private WebElement passwordField;

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/btn_login")
    @iOSXCUITFindBy(accessibility = "login-submit-button")
    private WebElement loginButton;

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/btn_biometric_login")
    @iOSXCUITFindBy(accessibility = "login-biometric-button")
    private WebElement biometricLoginButton;

    /** A widget unique to the login screen, used by {@link #isLoaded()}. */
    @AndroidFindBy(id = "com.fintech.wallet.sample:id/login_root")
    @iOSXCUITFindBy(accessibility = "login-root")
    private WebElement loginRoot;

    /** Composed component for entering the OTP digits. */
    private final OtpInputComponent otpInput = new OtpInputComponent();

    /** Composed component representing the platform biometric prompt. */
    private final BiometricPromptComponent biometricPrompt = new BiometricPromptComponent();

    /**
     * Types the username into the username field.
     *
     * @param username the test username.
     */
    public void enterUsername(String username) {
        log.info("Entering username on login page");
        typeText(usernameField, username);
    }

    /**
     * Types the password into the password field. The value is masked before logging.
     *
     * @param password the password (sourced from env, never hardcoded).
     */
    public void enterPassword(String password) {
        log.info("Entering password on login page");
        typeText(passwordField, password);
    }

    /** Taps the login/submit button. */
    public void tapLogin() {
        log.info("Tapping login button");
        tap(loginButton);
    }

    /**
     * Performs a full username/password login and completes the OTP challenge.
     *
     * @param user the username.
     * @param pass the password (env-sourced).
     * @param otp  the OTP provider used to fetch the one-time code for {@code user}.
     * @return the {@link DashboardPage} shown after a successful login.
     */
    public DashboardPage loginWithOtp(String user, String pass, OtpProvider otp) {
        log.info("Starting username/password + OTP login flow");
        enterUsername(user);
        enterPassword(pass);
        tapLogin();

        String code = otp.fetchOtp(user);
        otpInput.enterOtp(code);

        log.info("OTP submitted; expecting dashboard");
        return new DashboardPage();
    }

    /**
     * Performs a biometric login: triggers the biometric prompt, simulates a matching
     * biometric via {@link BiometricHelper}, and approves the prompt.
     *
     * @return the {@link DashboardPage} shown after a successful biometric login.
     */
    public DashboardPage loginWithBiometric() {
        log.info("Starting biometric login flow");
        tap(biometricLoginButton);

        if (biometricPrompt.isShown()) {
            // Simulate a matching biometric at the OS level, then accept in-app.
            BiometricHelper.match();
            biometricPrompt.approveWithMatch();
        } else {
            // Some builds drive the match purely via the simulator with no in-app prompt.
            BiometricHelper.match();
        }

        log.info("Biometric login completed; expecting dashboard");
        return new DashboardPage();
    }

    /**
     * @return {@code true} when the login screen is rendered (its root element is displayed).
     */
    public boolean isLoaded() {
        boolean loaded = isDisplayed(loginRoot);
        log.debug("LoginPage.isLoaded() = {}", loaded);
        return loaded;
    }
}
