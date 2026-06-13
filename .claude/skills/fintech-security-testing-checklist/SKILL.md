---
name: fintech-security-testing-checklist
description: Use when writing or reviewing wallet security tests - session timeout, biometric fallback, root/jailbreak, SSL-pinning, deep-link bypass, transaction backgrounding - and @REQ-*/@PCI-DSS-* tagging.
---

# Fintech Security Testing Checklist (mobile wallet)

Authoritative checklist for the security / non-functional test surface of the
synthetic fintech wallet. Apply this whenever you add or review a security
scenario in `src/test/resources/features/security.feature`, its glue in
`com.fintech.qa.stepdefinitions.SecuritySteps`, or any helper under
`com.fintech.qa.core.security`.

These tests run on Appium `io.appium.java_client:9.3.0` (Selenium 4 transitively)
+ Cucumber `7.20.1` on the JUnit 5 platform (`cucumber-junit-platform-engine
7.20.1`, `junit-platform-suite 1.11.3`), asserting with AssertJ
`3.26.3`. The runner is `com.fintech.qa.runners.CucumberTestRunner`; glue and
plugins are wired in `src/test/resources/junit-platform.properties`.

Golden rule: **a security scenario is a compliance artifact.** Every one is
traceable (`@REQ-SEC-*`), maps to a PCI-DSS control (`@PCI-DSS-*`), uses only
synthetic data, masks sensitive text before any log/report line, and simulates
OTP/biometrics — never real SMS, real sensors, or real PII/PAN.

---

## 0. The six security-relevant flows (current coverage)

`security.feature` is tagged `@security @non-functional` and today covers these
six flows. Five are already implemented (`@REQ-SEC-201`..`@REQ-SEC-205`); the
deep-link auth-bypass flow (`@REQ-SEC-206`) is the canonical next addition shown
below.

| # | Flow | REQ | PCI-DSS | What it proves |
|---|------|-----|---------|----------------|
| 1 | Session timeout / auto-logout | `@REQ-SEC-201` | `@PCI-DSS-8.1.8` | Idle session returns user to `LoginPage`; protected state is dropped |
| 2 | Biometric failure → password/OTP fallback | `@REQ-SEC-202` | `@PCI-DSS-8.3` | Non-matching biometric never reveals `DashboardPage`; fallback offered |
| 3 | Root / jailbreak detection prompt | `@REQ-SEC-203` | `@PCI-DSS-6.5` | Compromised device shows warning and blocks wallet functionality |
| 4 | SSL-pinning failure UX | `@REQ-SEC-204` | `@PCI-DSS-4.1` | Unpinned cert is refused, user warned, no privileged content loads |
| 5 | App backgrounding during a transaction | `@REQ-SEC-205` | `@PCI-DSS-8.1.8` | In-flight transfer never auto-confirms across background/resume; re-auth required |
| 6 | Deep-link auth-bypass attempt | `@REQ-SEC-206` | `@PCI-DSS-6.5` + `@PCI-DSS-8.2` | A deep link to a privileged screen on an unauthenticated session redirects to `LoginPage` |

For each flow the checklist below states: the precondition, the action
mechanism (how to drive it without `Thread.sleep` and without raw `findElement`),
the assertion shape, and the exact tags.

---

## 1. Cross-cutting rules (apply to EVERY security test)

These are non-negotiable in this repo. A scenario violating any is a
review-blocker.

- **No real PII/PAN/secrets.** Synthetic data only. Cards come from
  `com.fintech.qa.core.data.TestDataFactory` (Luhn-valid, clearly-fake `400000`
  test BIN via `LuhnGenerator`). Passwords/tokens come from env vars
  (`TEST_USER_PASSWORD`, `OTP_API_TOKEN`, `DEVICE_FARM_TOKEN`) — see
  `SecuritySteps.resolvePassword()`. Never hardcode secrets; `config.properties`
  holds only non-secret defaults.
- **Mask before any log/report/screenshot text.** Route every sensitive string
  through `com.fintech.qa.core.security.MaskingUtil.mask(...)`.
  `ExtentReportManager.logInfo/logPass/logFail` already mask internally; still
  never concatenate a raw PAN/OTP/account/token into a message. For sensitive
  screens use `ScreenshotUtil.capture(name, true)` which **skips** capture
  (returns `null`) — pixels cannot be masked.
- **OTP via `OtpProvider`, never real SMS.** Use `StaticOtpProvider` for
  local/CI or `TestApiOtpProvider` (test backend) via `OtpProviderFactory.create()`.
- **Biometrics via `BiometricHelper`, never a real sensor.** Platform-aware
  simulation: Android `mobile: fingerprint`, iOS `mobile: sendBiometricMatch`.
- **No `Thread.sleep`.** Use explicit waits (the `WebDriverWait` in `BasePage`)
  or app-lifecycle commands. Idle/timeout is simulated via app backgrounding, not
  a wall-clock sleep.
- **Only `BasePage` may call `driver.findElement`.** Steps/pages/components must
  not. App-management actions (backgrounding, deep links) go through the typed
  Appium capabilities on `DriverManager.getDriver()`, which are *not* element
  lookups and are therefore allowed in steps.
- **Negative assertions must assert absence of privilege.** A security failure
  path proves the dashboard is *not* loaded
  (`assertThat(new DashboardPage().isLoaded()).isFalse()`), the transfer is *not*
  successful, etc.

---

## 2. Tagging for audit traceability (`@REQ-*` / `@PCI-DSS-*`)

Order tags **suite → platform → requirement → compliance**. Domain tags
(`@security @non-functional`) live on the `Feature:` line and inherit to every
scenario.

```gherkin
@security @non-functional
Feature: Wallet security and resilience

  @regression @android @ios @REQ-SEC-201 @PCI-DSS-8.1.8
  Scenario: Session times out and auto-logs the user out
    ...
```

Rules:

- **Exactly one** of `@smoke` / `@regression` per scenario.
- **Both** `@android` and `@ios` unless the control is platform-specific.
- **At least one `@REQ-SEC-*`** — an untagged security scenario is untraceable
  and blocks review.
- **At least one `@PCI-DSS-*`** — every security scenario touches auth, transport,
  session, or secure-dev controls, so a PCI tag is always required.

PCI-DSS control families used here (extend, don't invent ad-hoc):

| Tag | Control area | Use for |
|-----|--------------|---------|
| `@PCI-DSS-8.1.8` | Idle session re-auth (15-min rule) | session timeout, backgrounding-during-transaction |
| `@PCI-DSS-8.2` | User authentication | login, deep-link auth-bypass |
| `@PCI-DSS-8.3` | Multi-factor / strong auth | biometric + OTP fallback |
| `@PCI-DSS-4.1` | Strong cryptography in transit | SSL-pinning |
| `@PCI-DSS-6.5` | Secure development / common vulns | root/jailbreak, deep-link bypass |

Pull scenarios for an audit purely by tag (no code change — matches the glue in
`junit-platform.properties`):

```bash
# All session-control evidence for an auditor
mvn test -Dcucumber.filter.tags="@PCI-DSS-8.1.8"

# A single requirement's evidence
mvn test -Dcucumber.filter.tags="@REQ-SEC-204"

# Android security regression, transport + session families
mvn test -Dcucumber.filter.tags="@security and @android and (@PCI-DSS-4.1 or @PCI-DSS-8.1.8)"
```

Every `@REQ-SEC-*` ↔ `@PCI-DSS-*` pairing in the table above is the audit
mapping. Keep a 1:1 (or 1:many) REQ→PCI mapping stable so traceability reports
don't drift.

---

## 3. Per-flow checklist

### 3.1 Session timeout / auto-logout — `@REQ-SEC-201 @PCI-DSS-8.1.8`

- **Precondition:** `Given an authenticated session on the dashboard` →
  `LoginPage.loginWithOtp(user, resolvePassword(), otpProvider)` then assert
  `DashboardPage.isLoaded()`.
- **Action (no sleep):** simulate idle by backgrounding past the idle window —
  `((InteractsWithApps) DriverManager.getDriver()).runAppInBackground(duration)`.
  Do **not** `Thread.sleep` for the timeout.
- **Assertion:** on resume a fresh `new LoginPage().isLoaded()` is `true`
  (protected state dropped).

```java
@When("the session is left idle until it times out")
public void the_session_is_left_idle_until_it_times_out() {
    backgroundAppFor(Duration.ofSeconds(2)); // crosses configured idle window; no Thread.sleep
    ExtentReportManager.logInfo("Simulated idle session timeout via app backgrounding");
}

@Then("the user is automatically logged out to the login screen")
public void the_user_is_automatically_logged_out_to_the_login_screen() {
    assertThat(new LoginPage().isLoaded())
            .as("auto-logout should return the user to the login screen").isTrue();
    ExtentReportManager.logPass("Session timed out; user auto-logged out to login screen");
}
```

### 3.2 Biometric failure → password/OTP fallback — `@REQ-SEC-202 @PCI-DSS-8.3`

- **Precondition:** `Given the wallet app is on the login screen`.
- **Action:** `BiometricHelper.nonMatch()` (Android unenrolled `fingerprintId`,
  iOS `sendBiometricMatch match=false`); if the OS prompt surfaces, deny it via
  `BiometricPromptComponent.denyWithNonMatch()`.
- **Assertion:** dashboard is NOT exposed (`new DashboardPage().isLoaded()` is
  `false`) AND the password/OTP fallback `LoginPage` is offered. OTP for any
  fallback login still comes from an `OtpProvider`, never a literal.

```java
@When("the user attempts biometric login with a non-matching biometric")
public void non_matching_biometric() {
    BiometricHelper.nonMatch();
    if (biometricPrompt.isShown()) {
        biometricPrompt.denyWithNonMatch();
    }
    ExtentReportManager.logInfo("Simulated non-matching biometric");
}
// Then: assertThat(new DashboardPage().isLoaded()).isFalse();  // rejected, not revealed
//       assertThat(new LoginPage().isLoaded()).isTrue();        // fallback offered
```

### 3.3 Root / jailbreak detection — `@REQ-SEC-203 @PCI-DSS-6.5`

- **Precondition:** app launched under simulated compromised conditions
  (`Given the wallet app is started on a rooted or jailbroken device`).
- **Assertion:** a warning is surfaced — assert `ToastComponent.isShown()` is
  `true` and `getMessage()` is non-blank — AND wallet functionality is blocked
  (`new DashboardPage().isLoaded()` is `false`).
- Report the warning text via `ExtentReportManager.logPass(...)` (auto-masked).

### 3.4 SSL-pinning failure UX — `@REQ-SEC-204 @PCI-DSS-4.1`

- **Precondition:** `Given an authenticated session on the dashboard`.
- **Action:** drive a request over a connection whose cert fails the pinned
  chain (the synthetic backend presents a non-pinned cert).
- **Assertion:** an SSL-pinning warning is displayed (`ToastComponent.isShown()`
  + non-blank `getMessage()`) AND the insecure request does not complete (no
  privileged content: `new DashboardPage().isLoaded()` is `false`).

### 3.5 App backgrounding during a transaction — `@REQ-SEC-205 @PCI-DSS-8.1.8`

- **Precondition:** authenticated dashboard + an in-progress transfer awaiting
  OTP confirmation built via real synthetic data:
  `dashboardPage.openTransfer().selectBeneficiary("Ada Testwell").enterAmount("25.00")`
  — note `enterAmount` takes a display-safe amount; the synthetic beneficiary /
  account come from `TestDataFactory` and surface only masked numbers.
- **Action:** `backgroundAppFor(Duration.ofSeconds(2))` then resume.
- **Assertion:** the transfer is NOT auto-confirmed
  (`transferPage.isTransferSuccessful()` is `false`) AND re-authentication is
  required (`new LoginPage().isLoaded()` is `true`).

### 3.6 Deep-link auth-bypass attempt — `@REQ-SEC-206 @PCI-DSS-6.5 @PCI-DSS-8.2`

The canonical *next* security test. Attempting to reach a privileged screen via a
deep link on an unauthenticated session must redirect to login, never render the
protected screen.

Gherkin (add to `security.feature`, keep declarative third-person phrasing):

```gherkin
  @regression @android @ios @REQ-SEC-206 @PCI-DSS-6.5 @PCI-DSS-8.2
  Scenario: Deep link to a privileged screen on an unauthenticated session is blocked
    Given the wallet app is on the login screen
    When a deep link to the transfer screen is opened without an authenticated session
    Then the user is redirected to the login screen
    And the privileged transfer screen is not shown
```

Step glue — the deep link is an app-management action (not an element lookup), so
it belongs in the step via the typed Appium capability, not in a page:

```java
@When("a deep link to the transfer screen is opened without an authenticated session")
public void deep_link_to_transfer_without_session() {
    // App-management action via the typed driver capability — not a findElement,
    // so it does not violate the BasePage-only rule and uses no Thread.sleep.
    DriverManager.getDriver().get("fintechwallet://transfer"); // synthetic test scheme
    ExtentReportManager.logInfo("Opened privileged deep link on an unauthenticated session");
}

@Then("the privileged transfer screen is not shown")
public void privileged_transfer_screen_not_shown() {
    assertThat(new TransferPage().isTransferSuccessful())
            .as("a deep link must not expose the privileged transfer flow without auth")
            .isFalse();
    assertThat(new LoginPage().isLoaded())
            .as("an unauthenticated deep link must redirect to login")
            .isTrue();
    ExtentReportManager.logPass("Deep-link auth-bypass blocked; redirected to login");
}
```

---

## 4. Reusable mechanisms (the "how", kept out of Gherkin)

### Backgrounding helper (lifecycle, not element lookup)

Lives as a private helper in `SecuritySteps`. Reuse it for timeout (3.1) and
backgrounding (3.5):

```java
/** Background the app for {@code duration} and resume — no Thread.sleep, no findElement. */
private static void backgroundAppFor(Duration duration) {
    ((InteractsWithApps) DriverManager.getDriver()).runAppInBackground(duration);
    log.debug("App backgrounded for {} and resumed", duration);
}
```

`InteractsWithApps` is implemented by both `AndroidDriver` and `IOSDriver`, so the
same code is platform-agnostic — `Platform.current()` already selected the right
driver upstream.

### Secrets from env only

```java
/** Test password strictly from env; clearly-fake local placeholder if unset. Never hardcoded. */
private static String resolvePassword() {
    String pw = System.getenv("TEST_USER_PASSWORD");
    if (pw == null || pw.isBlank()) {
        log.warn("TEST_USER_PASSWORD not set; using a clearly-fake local placeholder");
        return "Synthetic-Local-Pass!";
    }
    return pw;
}
```

### Masking sensitive output

Any string that could carry a PAN/CVV/OTP/account/IBAN/token must pass through
`MaskingUtil` before it lands in a log or report. The `ExtentReportManager`
facade masks for you; for raw slf4j use it explicitly:

```java
log.info("Confirming transfer to {}", MaskingUtil.maskAccountNumber(account));   // ****7421
ExtentReportManager.logPass("Transfer confirmed");                               // auto-masked
String shot = ScreenshotUtil.capture("otp-entry", /* sensitive */ true);          // skipped -> null
```

---

## 5. Review checklist (run before committing a security test)

- [ ] Scenario lives in `security.feature` under `@security @non-functional`.
- [ ] Exactly one of `@smoke` / `@regression`; platform tags present (both unless platform-specific).
- [ ] At least one `@REQ-SEC-*` AND at least one `@PCI-DSS-*`; the REQ↔PCI pairing matches the §0 mapping table.
- [ ] Steps are declarative third-person present tense — no "I", no element/locator/tap/sleep nouns.
- [ ] Negative path asserts **absence of privilege** (dashboard not loaded / transfer not successful / redirected to login) with AssertJ `.as(...)` descriptions.
- [ ] No `Thread.sleep` — timeouts/idle simulated via `runAppInBackground`; waits via `BasePage` `WebDriverWait`.
- [ ] No raw `driver.findElement` in steps/pages/components; only `BasePage` does element lookups. App-management actions (background, deep link) use typed Appium capabilities on `DriverManager.getDriver()`.
- [ ] OTP via `OtpProvider` (`StaticOtpProvider`/`TestApiOtpProvider`); biometrics via `BiometricHelper`. No real SMS/sensor.
- [ ] No real PII/PAN/secret literals anywhere. Synthetic data from `TestDataFactory` (`400000` BIN, Luhn-valid); passwords/tokens from env (`TEST_USER_PASSWORD`, `OTP_API_TOKEN`).
- [ ] All sensitive log/report text passes through `MaskingUtil`; sensitive screenshots use `ScreenshotUtil.capture(name, true)` (skipped).
- [ ] New step phrasing has a matching `@Given/@When/@Then` glue method in `com.fintech.qa.stepdefinitions.SecuritySteps`; existing phrasing reused verbatim.
