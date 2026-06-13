package com.fintech.qa.stepdefinitions;

import com.fintech.qa.core.reporting.ExtentReportManager;
import com.fintech.qa.core.security.OtpProvider;
import com.fintech.qa.core.security.StaticOtpProvider;
import com.fintech.qa.pages.DashboardPage;
import com.fintech.qa.pages.LoginPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thin step definitions for the login feature.
 *
 * <p>These steps contain no business logic and no raw waits: they delegate to {@link LoginPage} /
 * {@link DashboardPage} and assert with AssertJ. Credentials never appear as literals here &mdash;
 * the username is a synthetic test handle and the password is read from the {@code TEST_USER_PASSWORD}
 * environment variable (via {@code System.getenv}), so no secret is hardcoded. OTP codes are supplied
 * by a {@link StaticOtpProvider}, never a real SMS.</p>
 */
public class LoginSteps {

    private static final Logger log = LoggerFactory.getLogger(LoginSteps.class);

    /** Synthetic, clearly-fake test user handle (not real PII). */
    private static final String TEST_USERNAME = "test.user";

    /** OTP supplied by a static provider for local/dev runs; never a real SMS code. */
    private final OtpProvider otpProvider = new StaticOtpProvider();

    private LoginPage loginPage;
    private DashboardPage dashboardPage;

    @Given("the wallet app is launched on the login screen")
    public void the_wallet_app_is_launched_on_the_login_screen() {
        loginPage = new LoginPage();
        assertThat(loginPage.isLoaded())
                .as("login screen should be displayed at app launch")
                .isTrue();
        ExtentReportManager.logPass("Login screen is displayed");
    }

    @When("the user enters a valid username and password")
    public void the_user_enters_a_valid_username_and_password() {
        loginPage.enterUsername(TEST_USERNAME);
        loginPage.enterPassword(resolvePassword());
        ExtentReportManager.logInfo("Entered synthetic username and env-sourced password");
    }

    @When("the user submits the login form")
    public void the_user_submits_the_login_form() {
        loginPage.tapLogin();
    }

    @When("the user completes the OTP challenge")
    public void the_user_completes_the_otp_challenge() {
        dashboardPage = loginPage.loginWithOtp(TEST_USERNAME, resolvePassword(), otpProvider);
    }

    @When("the user logs in with valid credentials and OTP")
    public void the_user_logs_in_with_valid_credentials_and_otp() {
        dashboardPage = loginPage.loginWithOtp(TEST_USERNAME, resolvePassword(), otpProvider);
    }

    @When("the user authenticates with biometrics")
    public void the_user_authenticates_with_biometrics() {
        dashboardPage = loginPage.loginWithBiometric();
    }

    @Then("the dashboard is displayed")
    public void the_dashboard_is_displayed() {
        assertThat(dashboardPage)
                .as("a dashboard page should have been produced by the login flow")
                .isNotNull();
        assertThat(dashboardPage.isLoaded())
                .as("dashboard should be displayed after a successful login")
                .isTrue();
        ExtentReportManager.logPass("Dashboard is displayed after successful login");
    }

    @Then("the masked account balance is shown")
    public void the_masked_account_balance_is_shown() {
        String balance = dashboardPage.getMaskedBalance();
        assertThat(balance)
                .as("dashboard should expose a (masked) balance value")
                .isNotBlank();
        // Reported text is masked again defensively by ExtentReportManager.
        ExtentReportManager.logInfo("Masked balance read from dashboard: " + balance);
    }

    @Then("the login screen is still displayed")
    public void the_login_screen_is_still_displayed() {
        assertThat(loginPage.isLoaded())
                .as("login screen should remain after a rejected login attempt")
                .isTrue();
        ExtentReportManager.logPass("Login screen still displayed (login not granted)");
    }

    /**
     * Resolves the test password strictly from the environment ({@code TEST_USER_PASSWORD}),
     * never from a literal or {@code config.properties}. Falls back to a clearly-fake local
     * placeholder when unset so local/dev runs against a synthetic app still execute.
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
