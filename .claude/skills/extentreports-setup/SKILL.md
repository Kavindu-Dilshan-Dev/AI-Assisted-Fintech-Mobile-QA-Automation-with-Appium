---
name: extentreports-setup
description: Use when wiring or fixing ExtentReports for this Cucumber7 fintech QA repo — the grasshopper single-engine adapter, extent.properties, screenshot-on-failure hooks, and MaskingUtil redaction of PAN/OTP/token/account text before anything reaches the report.
---

# ExtentReports Setup (Cucumber 7 + grasshopper adapter, single engine)

This skill governs reporting for the synthetic fintech wallet QA framework. The
**golden rule** is: there is exactly **ONE** Extent engine in this repo — the
grasshopper `extentreports-cucumber7-adapter`. Never construct a second
`com.aventstack.extentreports.ExtentReports`. All log/screenshot calls go through
the facade `com.fintech.qa.core.reporting.ExtentReportManager`, which delegates to
the adapter, and every string is masked by
`com.fintech.qa.core.security.MaskingUtil` first.

## When to use this skill

- Adding/modifying reporting wiring (`extent.properties`, `extent-spark-config.xml`,
  `junit-platform.properties` plugin line).
- Editing the failure-screenshot hooks in `CucumberHooks`.
- Anyone is tempted to `new ExtentReports()`, `ExtentSparkReporter`, attach images,
  or write report text — route them through `ExtentReportManager` instead.
- Diagnosing why a card/OTP/token/account number leaked into `Spark.html`, or why a
  sensitive screen's screenshot was (correctly) skipped.

## Pinned versions (do not change)

| Coordinate | Version |
|---|---|
| `com.aventstack:extentreports` | `5.1.2` |
| `tech.grasshopper:extentreports-cucumber7-adapter` | `1.14.0` |
| `io.cucumber:cucumber-java` | `7.20.1` |
| `io.cucumber:cucumber-junit-platform-engine` | `7.20.1` |
| `org.junit.platform:junit-platform-suite` | `1.11.3` |
| `org.junit.jupiter:junit-jupiter` | `5.11.3` |
| `com.fasterxml.jackson.core:jackson-databind` | `2.18.1` |
| `org.slf4j:slf4j-api` / `ch.qos.logback:logback-classic` | `2.0.16` / `1.5.12` |

Selenium is **not** declared explicitly — it arrives transitively via
`io.appium:java-client:9.3.0`.

## How the wiring fits together (one engine, three files)

```
features run on JUnit Platform "cucumber" engine
        │
        │  junit-platform.properties  ──►  cucumber.plugin = ...ExtentCucumberAdapter:
        ▼
ExtentCucumberAdapter  (THE single Extent engine)
        │  configured by  ──►  extent.properties  ──►  extent-spark-config.xml (branding)
        │
        ├── ExtentReportManager (facade) ──► addTestStepLog / getCurrentStep().log(status,..)
        │                                    addTestStepScreenCaptureFromPath
        │        ▲ every arg masked by MaskingUtil
        │
        └── CucumberHooks @AfterStep/@After ──► ScreenshotUtil.capture(...) ──► attachScreenshot
```

The three resource files are the **only** place the engine is configured. The
adapter is also listed in `cucumber.glue` so its hooks initialize the report.

### 1. `src/test/resources/junit-platform.properties` (contractually fixed)

```properties
cucumber.glue=com.fintech.qa.stepdefinitions,com.fintech.qa.hooks,com.aventstack.extentreports.cucumber.adapter
cucumber.plugin=pretty, com.aventstack.extentreports.cucumber.adapter.ExtentCucumberAdapter:, json:target/cucumber.json, junit:target/cucumber.xml
cucumber.publish.quiet=true
```

- The trailing colon in `ExtentCucumberAdapter:` is **required** (plugin-with-arg syntax).
- The adapter package in `cucumber.glue` is what bootstraps the Extent engine. Do not
  remove it, and do not add a second reporting plugin.

### 2. `src/test/resources/extent.properties` (the single-engine config)

Spark (HTML) is the only enabled reporter; all others are off. Screenshots are
**not** inlined as base64 (`extent.reporter.spark.base64imagesrc=false`) so we
control the on-disk location and the masking/sensitive-skip policy ourselves.

```properties
extent.reporter.spark.start=true
extent.reporter.spark.out=target/extent-report/Spark.html
extent.reporter.spark.base64imagesrc=false

# single-engine policy: everything else OFF
extent.reporter.html.start=false
extent.reporter.pdf.start=false
extent.reporter.logger.start=false
extent.reporter.klov.start=false

# screenshot handling — must stay in sync with ScreenshotUtil DEFAULT_DIR
screenshot.dir=target/screenshots/
screenshot.rel.path=../screenshots/
screenshot.events=Failed

extent.reporter.spark.config=src/test/resources/extent-spark-config.xml
extent.reporter.spark.vieworder=dashboard,test,exception,author,device,log
```

`screenshot.rel.path=../screenshots/` resolves from `target/extent-report/Spark.html`
back to `target/screenshots/`, so the report links the externally-stored PNGs.

The `screenshot.dir` value here **must match** `ScreenshotUtil.DEFAULT_DIR`
(`target/screenshots`); both read the `screenshot.dir` config key. If you move one,
move the other.

### 3. `src/test/resources/extent-spark-config.xml` (branding only)

Presentation only (title, theme, timestamp). Never put behavior/redaction here.

## The facade: `ExtentReportManager` (never bypass it)

Public API — all four methods mask via `MaskingUtil` before touching the adapter,
and are null-safe (a reporting hiccup never fails a test):

```java
ExtentReportManager.logInfo(String message);   // adapter.addTestStepLog (masked)
ExtentReportManager.logPass(String message);   // getCurrentStep().log(PASS, masked) or fallback
ExtentReportManager.logFail(String message);   // getCurrentStep().log(FAIL, masked) or fallback
ExtentReportManager.attachScreenshot(String path); // addTestStepScreenCaptureFromPath (masked)
```

Internals to respect:
- It uses `ExtentCucumberAdapter.getCurrentStep()` to get the live `ExtentTest` node
  for PASS/FAIL styling, with a graceful fallback to `addTestStepLog` when no step is
  active (e.g. between scenarios).
- `attachScreenshot(null)` / blank is a **no-op** — this is deliberate so callers can
  pipe `ScreenshotUtil.capture(name, sensitive)` straight in even when it returns
  `null` for a skipped sensitive screen.

DO NOT, anywhere in main or test code:

```java
// ❌ forbidden — creates a second engine, breaks the single-engine contract
ExtentReports extent = new ExtentReports();
extent.attachReporter(new ExtentSparkReporter("..."));

// ❌ forbidden — unmasked text straight into the adapter
ExtentCucumberAdapter.addTestStepLog("OTP was " + otp);
```

Instead:

```java
// ✅ masked, single engine
ExtentReportManager.logInfo("Submitted OTP for user " + userId); // OTP value never logged anyway
ExtentReportManager.logFail("Transfer rejected for account " + acct); // acct masked to ****1234
```

## Screenshot-on-failure hook (`CucumberHooks`)

The hooks already implement the policy. Keep this shape when editing:

```java
@AfterStep
public void afterStep(Scenario scenario) {
    if (!scenario.isFailed()) {
        return;
    }
    String path = ScreenshotUtil.capture("step-failure-" + safeName(scenario));
    if (path != null) {                       // null => skipped/sensitive/no-driver
        ExtentReportManager.logFail("Step failed; attaching screenshot");
        ExtentReportManager.attachScreenshot(path);
    }
}

@After(order = 0)
public void tearDown(Scenario scenario) {
    try {
        if (scenario.isFailed()) {
            String path = ScreenshotUtil.capture("scenario-failure-" + safeName(scenario));
            if (path != null) {
                ExtentReportManager.logFail("Scenario failed: " + scenario.getName());
                ExtentReportManager.attachScreenshot(path);
            }
        } else {
            ExtentReportManager.logPass("Scenario passed: " + scenario.getName());
        }
    } finally {
        DriverManager.quitDriver();           // always release the thread-local session
    }
}
```

Rules baked in here:
- `safeName(scenario)` strips everything outside `[A-Za-z0-9._-]` — **never** put
  user-entered values, amounts, or secrets into a screenshot file name or scenario log.
- Failure screenshots use the **non-sensitive** `capture(name)` path. A screen that may
  render an on-device PAN/CVV/balance must be flagged sensitive at the step level (see
  below) so it is never captured.
- `DriverManager.quitDriver()` runs in `finally` so the thread-local session is released
  even if reporting throws.

## Why screenshot pixels cannot be masked — the sensitive-skip strategy

`MaskingUtil` redacts **text** (regex over strings). A screenshot is a raster image;
a banking app legitimately renders a full PAN, CVV, OTP, or account balance on screen,
and there is no reliable way to scrub those pixels after the fact. So the policy is:
**do not capture sensitive screens at all.**

`ScreenshotUtil.capture(String name, boolean sensitive)` enforces this:

```java
// ✅ on a screen that shows card/CVV/OTP/balance — DO NOT take the shot
String shot = ScreenshotUtil.capture("card-details", /* sensitive */ true);
// shot == null  -> a compliance note is logged; nothing is written to disk
ExtentReportManager.attachScreenshot(shot); // no-op because shot is null

// ✅ on a neutral screen — normal capture
String ok = ScreenshotUtil.capture("dashboard-loaded", false);
ExtentReportManager.attachScreenshot(ok);
```

- `sensitive == true` → capture is **skipped**, returns `null`, logs an auditable note.
- `sensitive == false` → delegates to `capture(name)`, writes
  `target/screenshots/<name>-<counter>.png`, returns the absolute path.
- The `<counter>` is a process-wide `AtomicInteger`, so parallel captures never collide.
- The path/name are masked via `MaskingUtil` before being logged, because file names
  themselves can leak identifiers.

Decision rule for authors: **if a screen can display a PAN, CVV, OTP, full account
number, IBAN, or token, pass `sensitive = true`.** When in doubt, treat it as sensitive.

## What `MaskingUtil.mask` redacts (so report text is safe)

Applied in order (structured tokens first, then bare numerics) so a long secret is not
half-eaten by a looser numeric rule:

1. JWT (`a.b.c` base64url) → `[REDACTED]`
2. `Bearer <token>` → `Bearer [REDACTED]`
3. IBAN → keep last 4 (`************3000`)
4. Labelled `CVV/CVC/security code` → `cvv: [REDACTED]`
5. Labelled `OTP/one-time/verification/passcode/auth code` → `otp: [REDACTED]`
6. Labelled account numbers → masked, last 4 kept
7. PAN (13–19 digits, spaced/dashed) → `**** **** **** 1234`
8. Standalone 7–12 digit account runs → last 4 kept

Helpers: `MaskingUtil.maskCardNumber(s)` → `"**** **** **** 1234"`;
`MaskingUtil.maskAccountNumber(s)` keeps last 4. All null-safe and pure.

Because `ExtentReportManager` masks **every** argument, even an accidental
`logInfo("PAN 4000123412341234")` lands in the report as `PAN **** **** **** 1234`.
This is a safety net — do not rely on it; still avoid passing raw secrets.

## Outputs and .gitignore

- Spark HTML: `target/extent-report/Spark.html`
- Screenshots: `target/screenshots/<name>-<counter>.png`
- Cucumber JSON/XML: `target/cucumber.json`, `target/cucumber.xml`

All of `target/`, `target/screenshots/`, and `target/extent-report/` are already
git-ignored. Reports/screenshots are build artifacts — never commit them, and never
commit a screenshot of a sensitive screen (the policy above means one should not exist).

## Verification checklist

- [ ] Exactly one engine: grep for `new ExtentReports`, `ExtentSparkReporter`,
      `attachReporter` → must be **zero** hits outside the grasshopper jar.
- [ ] All report writes go through `ExtentReportManager` (no direct
      `ExtentCucumberAdapter.addTestStepLog` in steps/pages/hooks except inside the facade).
- [ ] `screenshot.dir` in `extent.properties` matches `ScreenshotUtil.DEFAULT_DIR`.
- [ ] `cucumber.plugin` line contains the adapter with its trailing colon; `cucumber.glue`
      includes `com.aventstack.extentreports.cucumber.adapter`.
- [ ] Sensitive screens use `ScreenshotUtil.capture(name, true)`; failure hooks check for
      `null` before attaching.
- [ ] After `mvn test`, open `target/extent-report/Spark.html` and confirm no unmasked
      PAN/CVV/OTP/account/token appears in any step log.
