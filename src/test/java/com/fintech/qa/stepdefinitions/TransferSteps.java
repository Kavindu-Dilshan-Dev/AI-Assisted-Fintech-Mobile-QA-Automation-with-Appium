package com.fintech.qa.stepdefinitions;

import com.fintech.qa.components.ToastComponent;
import com.fintech.qa.core.data.TestDataFactory;
import com.fintech.qa.core.data.model.Account;
import com.fintech.qa.core.data.model.Beneficiary;
import com.fintech.qa.core.data.model.Transaction;
import com.fintech.qa.core.reporting.ExtentReportManager;
import com.fintech.qa.core.security.OtpProvider;
import com.fintech.qa.core.security.StaticOtpProvider;
import com.fintech.qa.pages.DashboardPage;
import com.fintech.qa.pages.LoginPage;
import com.fintech.qa.pages.TransferPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thin step definitions for the transfer (money movement) feature.
 *
 * <p>All test data is synthetic and produced by {@link TestDataFactory} (Luhn-valid fake cards,
 * masked account numbers, fake beneficiaries) &mdash; no real PII/PAN ever appears in a step. The
 * transfer is confirmed with an OTP obtained from a {@link StaticOtpProvider}, never a real SMS.
 * Steps delegate entirely to {@link DashboardPage} / {@link TransferPage} and assert with AssertJ;
 * they contain no business logic and no raw waits.</p>
 */
public class TransferSteps {

    private static final Logger log = LoggerFactory.getLogger(TransferSteps.class);

    /** Synthetic test user handle (not real PII). */
    private static final String TEST_USERNAME = "test.user";

    /** OTP for transfer confirmation, supplied statically for local/dev runs. */
    private final OtpProvider otpProvider = new StaticOtpProvider();

    /** Confirmation/error message surface (documented message component). */
    private final ToastComponent toast = new ToastComponent();

    /** Synthetic source account for the transfer scenarios. */
    private final Account sourceAccount = TestDataFactory.checkingAccount();

    /** Synthetic destination beneficiary for the transfer scenarios. */
    private final Beneficiary beneficiary = TestDataFactory.beneficiary();

    private DashboardPage dashboardPage;
    private TransferPage transferPage;
    private boolean transferConfirmed;

    @Given("the user is logged in and on the dashboard")
    public void the_user_is_logged_in_and_on_the_dashboard() {
        LoginPage loginPage = new LoginPage();
        assertThat(loginPage.isLoaded())
                .as("login screen should be displayed before logging in")
                .isTrue();
        dashboardPage = loginPage.loginWithOtp(TEST_USERNAME, resolvePassword(), otpProvider);
        assertThat(dashboardPage.isLoaded())
                .as("dashboard should be displayed after login")
                .isTrue();
        ExtentReportManager.logPass("Logged in and dashboard displayed");
    }

    @When("the user opens the transfer screen")
    public void the_user_opens_the_transfer_screen() {
        transferPage = dashboardPage.openTransfer();
        ExtentReportManager.logInfo("Opened transfer screen");
    }

    @When("the user navigates to transfers via the bottom navigation")
    public void the_user_navigates_to_transfers_via_the_bottom_navigation() {
        transferPage = dashboardPage.bottomNav().goToTransfers();
        ExtentReportManager.logInfo("Opened transfer screen via bottom navigation");
    }

    @When("the user selects the synthetic beneficiary")
    public void the_user_selects_the_synthetic_beneficiary() {
        transferPage.selectBeneficiary(beneficiary.getName());
        // Only the masked account number is ever reported.
        ExtentReportManager.logInfo("Selected beneficiary account " + beneficiary.getMaskedAccountNumber());
    }

    @When("the user enters a transfer amount of {string}")
    public void the_user_enters_a_transfer_amount_of(String amount) {
        Transaction txn = TestDataFactory.transfer(sourceAccount, beneficiary, amount);
        transferPage.enterAmount(amount);
        ExtentReportManager.logInfo("Entered transfer amount " + amount
                + " from account " + txn.getFrom().getMaskedAccountNumber()
                + " to " + txn.getTo().getMaskedAccountNumber());
    }

    @When("the user confirms the transfer with OTP")
    public void the_user_confirms_the_transfer_with_otp() {
        transferConfirmed = transferPage.confirmWithOtp(otpProvider);
    }

    @Then("the transfer is successful")
    public void the_transfer_is_successful() {
        assertThat(transferConfirmed)
                .as("transfer confirmation (post-OTP) should report success")
                .isTrue();
        assertThat(transferPage.isTransferSuccessful())
                .as("transfer success indicator/toast should be shown")
                .isTrue();
        ExtentReportManager.logPass("Transfer completed successfully");
    }

    @Then("a confirmation message is shown")
    public void a_confirmation_message_is_shown() {
        assertThat(toast.isShown())
                .as("a transfer confirmation toast/snackbar should be surfaced")
                .isTrue();
        String message = toast.getMessage();
        assertThat(message)
                .as("the transfer confirmation message should not be blank")
                .isNotBlank();
        // Reported text is masked again defensively by ExtentReportManager.
        ExtentReportManager.logInfo("Transfer confirmation message: " + message);
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
