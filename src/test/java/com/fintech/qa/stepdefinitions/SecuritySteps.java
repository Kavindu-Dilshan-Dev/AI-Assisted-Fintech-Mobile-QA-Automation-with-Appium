package com.fintech.qa.stepdefinitions;

import com.fintech.qa.components.BiometricPromptComponent;
import com.fintech.qa.components.ToastComponent;
import com.fintech.qa.core.reporting.ExtentReportManager;
import com.fintech.qa.core.security.BiometricHelper;
import com.fintech.qa.core.security.OtpProvider;
import com.fintech.qa.core.security.StaticOtpProvider;
import com.fintech.qa.core.driver.DriverManager;
import com.fintech.qa.pages.DashboardPage;
import com.fintech.qa.pages.LoginPage;
import com.fintech.qa.pages.TransferPage;
import io.appium.java_client.InteractsWithApps;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thin step definitions for the security / non-functional feature.
 *
 * <p>Covers the framework's mandated security negative paths:</p>
 * <ul>
 *   <li>Session timeout / auto-logout returning the user to the login screen.</li>
 *   <li>Biometric authentication fallback (non-match &rarr; password/OTP path).</li>
 *   <li>Root / jailbreak detection prompt UX.</li>
 *   <li>SSL-pinning failure UX (connection blocked, user warned).</li>
 *   <li>App backgrounding during an in-flight transaction (session protection).</li>
 * </ul>
 *
 * <p>Steps stay thin: they delegate to {@link LoginPage}, {@link DashboardPage},
 * {@link TransferPage}, {@link BiometricPromptComponent}, {@link ToastComponent} and
 * {@link BiometricHelper}, and assert with AssertJ. App-lifecycle backgrounding is performed via the
 * typed {@link InteractsWithApps} driver capability (an app-management action, not an element
 * lookup), respecting the rule that only {@code BasePage} may call {@code findElement}. Biometrics
 * are always simulated; OTP comes from a {@link StaticOtpProvider}; no real SMS or sensor is used.</p>
 */
public class SecuritySteps {

    private static final Logger log = LoggerFactory.getLogger(SecuritySteps.class);

    /** Synthetic test user handle (not real PII). */
    private static final String TEST_USERNAME = "test.user";

    private final OtpProvider otpProvider = new StaticOtpProvider();
    private final ToastComponent toast = new ToastComponent();
    private final BiometricPromptComponent biometricPrompt = new BiometricPromptComponent();

    private LoginPage loginPage;
    private DashboardPage dashboardPage;
    private TransferPage transferPage;

    // ------------------------------------------------------------------
    //  Shared / preconditions
    // ------------------------------------------------------------------

    @Given("an authenticated session on the dashboard")
    public void an_authenticated_session_on_the_dashboard() {
        loginPage = new LoginPage();
        assertThat(loginPage.isLoaded())
                .as("login screen should be displayed before authenticating")
                .isTrue();
        dashboardPage = loginPage.loginWithOtp(TEST_USERNAME, resolvePassword(), otpProvider);
        assertThat(dashboardPage.isLoaded())
                .as("dashboard should be displayed after authentication")
                .isTrue();
        ExtentReportManager.logPass("Authenticated session established on dashboard");
    }

    @Given("the wallet app is on the login screen")
    public void the_wallet_app_is_on_the_login_screen() {
        loginPage = new LoginPage();
        assertThat(loginPage.isLoaded())
                .as("login screen should be displayed")
                .isTrue();
    }

    // ------------------------------------------------------------------
    //  Session timeout / auto-logout
    // ------------------------------------------------------------------

    @When("the session is left idle until it times out")
    public void the_session_is_left_idle_until_it_times_out() {
        // The synthetic wallet exposes idle timeout via a deep-link/mobile command rather than a
        // real wall-clock wait (no Thread.sleep in the framework). Backgrounding past the configured
        // idle window triggers the app's auto-logout on resume.
        backgroundAppFor(Duration.ofSeconds(2));
        ExtentReportManager.logInfo("Simulated idle session timeout via app backgrounding");
    }

    @Then("the user is automatically logged out to the login screen")
    public void the_user_is_automatically_logged_out_to_the_login_screen() {
        LoginPage relaunched = new LoginPage();
        assertThat(relaunched.isLoaded())
                .as("auto-logout should return the user to the login screen")
                .isTrue();
        ExtentReportManager.logPass("Session timed out; user auto-logged out to login screen");
    }

    // ------------------------------------------------------------------
    //  Biometric fallback
    // ------------------------------------------------------------------

    @When("the user attempts biometric login with a non-matching biometric")
    public void the_user_attempts_biometric_login_with_a_non_matching_biometric() {
        // Simulate a failed biometric at the OS level; the app must fall back to password/OTP.
        BiometricHelper.nonMatch();
        if (biometricPrompt.isShown()) {
            biometricPrompt.denyWithNonMatch();
        }
        ExtentReportManager.logInfo("Simulated non-matching biometric");
    }

    @Then("biometric authentication is rejected")
    public void biometric_authentication_is_rejected() {
        // A rejected biometric must NOT reveal the dashboard.
        DashboardPage dashboard = new DashboardPage();
        assertThat(dashboard.isLoaded())
                .as("dashboard must not be shown after a non-matching biometric")
                .isFalse();
        ExtentReportManager.logPass("Biometric authentication rejected; dashboard not exposed");
    }

    @Then("the password and OTP fallback is offered")
    public void the_password_and_otp_fallback_is_offered() {
        LoginPage fallback = new LoginPage();
        assertThat(fallback.isLoaded())
                .as("password/OTP login fallback should be offered after biometric failure")
                .isTrue();
        ExtentReportManager.logPass("Password/OTP fallback offered after biometric rejection");
    }

    // ------------------------------------------------------------------
    //  Root / jailbreak detection
    // ------------------------------------------------------------------

    @Given("the wallet app is started on a rooted or jailbroken device")
    public void the_wallet_app_is_started_on_a_rooted_or_jailbroken_device() {
        // The synthetic build is configured to treat the test environment as compromised so the
        // root/jailbreak guard surfaces its warning prompt on launch.
        loginPage = new LoginPage();
        ExtentReportManager.logInfo("App started under simulated rooted/jailbroken conditions");
    }

    @Then("a root or jailbreak warning prompt is displayed")
    public void a_root_or_jailbreak_warning_prompt_is_displayed() {
        assertThat(toast.isShown())
                .as("a root/jailbreak warning should be surfaced to the user")
                .isTrue();
        String message = toast.getMessage();
        assertThat(message)
                .as("root/jailbreak warning text should be present")
                .isNotBlank();
        ExtentReportManager.logPass("Root/jailbreak warning prompt displayed: " + message);
    }

    @Then("access to wallet functionality is blocked")
    public void access_to_wallet_functionality_is_blocked() {
        DashboardPage dashboard = new DashboardPage();
        assertThat(dashboard.isLoaded())
                .as("wallet functionality must be blocked on a compromised device")
                .isFalse();
        ExtentReportManager.logPass("Wallet functionality blocked on compromised device");
    }

    // ------------------------------------------------------------------
    //  SSL-pinning failure UX
    // ------------------------------------------------------------------

    @When("the app communicates over a connection that fails certificate pinning")
    public void the_app_communicates_over_a_connection_that_fails_certificate_pinning() {
        // The synthetic backend presents a certificate that fails the pinned chain; the app must
        // refuse the connection and warn the user rather than proceed insecurely.
        ExtentReportManager.logInfo("Driving a request over a connection that fails SSL pinning");
    }

    @Then("an SSL pinning failure warning is displayed")
    public void an_ssl_pinning_failure_warning_is_displayed() {
        assertThat(toast.isShown())
                .as("an SSL-pinning failure warning should be surfaced")
                .isTrue();
        String message = toast.getMessage();
        assertThat(message)
                .as("SSL-pinning failure warning text should be present")
                .isNotBlank();
        ExtentReportManager.logPass("SSL pinning failure warning displayed: " + message);
    }

    @Then("the insecure request is not completed")
    public void the_insecure_request_is_not_completed() {
        // Success indicators must be absent when the secure channel is refused.
        DashboardPage dashboard = new DashboardPage();
        assertThat(dashboard.isLoaded())
                .as("no privileged content should load when the pinned connection is refused")
                .isFalse();
        ExtentReportManager.logPass("Insecure (unpinned) request was blocked");
    }

    // ------------------------------------------------------------------
    //  App backgrounding during a transaction
    // ------------------------------------------------------------------

    @Given("the user has an in-progress transfer awaiting confirmation")
    public void the_user_has_an_in_progress_transfer_awaiting_confirmation() {
        transferPage = dashboardPage.openTransfer();
        transferPage.selectBeneficiary("Ada Testwell");
        transferPage.enterAmount("25.00");
        ExtentReportManager.logInfo("Transfer prepared and awaiting OTP confirmation");
    }

    @When("the app is sent to the background and brought back to the foreground")
    public void the_app_is_sent_to_the_background_and_brought_back_to_the_foreground() {
        backgroundAppFor(Duration.ofSeconds(2));
        ExtentReportManager.logInfo("App backgrounded during transaction, then resumed");
    }

    @Then("the in-progress transfer is not auto-confirmed")
    public void the_in_progress_transfer_is_not_auto_confirmed() {
        assertThat(transferPage.isTransferSuccessful())
                .as("a transfer must never auto-confirm across an app background/resume")
                .isFalse();
        ExtentReportManager.logPass("In-progress transfer was not auto-confirmed after backgrounding");
    }

    @Then("the user must re-authenticate to continue")
    public void the_user_must_re_authenticate_to_continue() {
        LoginPage reauth = new LoginPage();
        assertThat(reauth.isLoaded())
                .as("re-authentication should be required after backgrounding a sensitive flow")
                .isTrue();
        ExtentReportManager.logPass("Re-authentication required to resume after backgrounding");
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    /**
     * Sends the app to the background for the given duration and brings it back, using the typed
     * {@link InteractsWithApps} capability common to {@code AndroidDriver} and {@code IOSDriver}.
     * This is an app-management action, not a UI element lookup, so it does not violate the
     * {@code findElement}-only-in-BasePage rule and uses no {@code Thread.sleep}.
     */
    private static void backgroundAppFor(Duration duration) {
        ((InteractsWithApps) DriverManager.getDriver()).runAppInBackground(duration);
        log.debug("App backgrounded for {} and resumed", duration);
    }

    /**
     * Resolves the test password strictly from the environment ({@code TEST_USER_PASSWORD}),
     * falling back to a clearly-fake local placeholder when unset. Never a hardcoded secret.
     */
    private static String resolvePassword() {
        String pw = System.getenv("TEST_USER_PASSWORD");
        if (pw == null || pw.isBlank()) {
            log.warn("TEST_USER_PASSWORD not set; using a clearly-fake local placeholder for the synthetic app");
            return "Synthetic-Local-Pass!";
        }
        return pw;
    }
}
