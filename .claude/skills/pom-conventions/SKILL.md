---
name: pom-conventions
description: Use when writing or reviewing Appium page objects, components, or driver code in com.fintech.qa - covers BasePage helpers, @AndroidFindBy/@iOSXCUITFindBy, DriverManager ThreadLocal, no-raw-findElement
---

# Page Object Model (POM) Conventions

This skill defines how page objects, UI components, and driver plumbing are written
in the fintech mobile QA framework. Follow it whenever you create or modify anything
under `src/main/java/com/fintech/qa/pages`, `.../components`, or `.../core/driver`.

Pinned stack (do not change): `io.appium:java-client:9.3.0` (Selenium 4 transitive),
Java 17, Maven single module. Appium imports come from `io.appium.java_client.*`.

---

## 1. The package layout (where each class lives)

Main sources root: `src/main/java/com/fintech/qa`. Never invent new top-level packages.

```
core.config        ConfigManager
core.driver        Platform(enum ANDROID,IOS), CapabilitiesBuilder, DriverFactory, DriverManager
core.base          BasePage(abstract), SwipeDirection(enum)
core.reporting     ExtentReportManager, ScreenshotUtil
core.security      MaskingUtil, OtpProvider, TestApiOtpProvider, StaticOtpProvider,
                   OtpProviderFactory, BiometricHelper
core.data          LuhnGenerator, TestDataFactory
core.data.model    Account, Card, Beneficiary, Transaction
core.data.builder  AccountBuilder, CardBuilder, BeneficiaryBuilder, TransactionBuilder
core.locators      LocatorRepository
components          BottomNavComponent, OtpInputComponent, BiometricPromptComponent,
                   ToastComponent   (ALL extend BasePage)
pages              LoginPage, DashboardPage, TransferPage   (extend BasePage)
```

Rule of thumb: a **screen** is a `pages.*Page`; a **reusable widget that appears on many
screens** (nav bar, OTP grid, biometric prompt, toast) is a `components.*Component`.
Both extend `BasePage`.

---

## 2. The cardinal rule: only BasePage may call `driver.findElement`

`com.fintech.qa.core.base.BasePage` is the single class permitted to touch the raw
`AppiumDriver` for element lookup, interaction, or gestures.

- Pages, components, step definitions, and hooks **must not** call `driver.findElement`,
  `driver.findElements`, `element.click()`, `element.sendKeys(...)`, or build `Actions`
  themselves.
- They interact **only** through the protected helpers on `BasePage`.
- Page-object fields are `@AndroidFindBy`/`@iOSXCUITFindBy` proxies; pass those `WebElement`
  fields into the `BasePage` helpers.

If you find yourself needing a raw driver call in a page/component, that capability
belongs on `BasePage` — add a helper there instead.

---

## 3. BasePage: the only sanctioned API (exact signatures)

`BasePage` is `abstract` and provides these fields and methods. Subclasses inherit them;
do not redeclare `driver`, `wait`, or `log`.

```java
public abstract class BasePage {
    protected static final Logger log;        // org.slf4j.Logger, shared
    protected final AppiumDriver driver;       // thread-bound, from DriverManager
    protected final WebDriverWait wait;        // explicit.wait.seconds (default 20)

    protected BasePage();                       // see constructor below

    // Waits
    protected WebElement waitForVisible(WebElement element);
    protected WebElement waitForVisible(By locator);
    protected WebElement waitForClickable(WebElement element);

    // Interactions
    protected void    tap(WebElement element);
    protected void    typeText(WebElement element, String text);  // logs MaskingUtil.mask(text)
    protected String  getText(WebElement element);
    protected boolean isDisplayed(WebElement element);            // null/absence-safe, no throw

    // Gestures (W3C PointerInput — NEVER Thread.sleep)
    protected void swipe(SwipeDirection direction);
    protected void scrollToElement(WebElement element);

    // Context switching (hybrid / 3DS / SSL-pinning UX screens)
    protected void switchToWebView();
    protected void switchToNative();

    // Accessibility (a11y assertion support)
    protected String contentDescription(WebElement element);
}
```

The constructor is fixed — replicate it exactly when reasoning about subclasses:

```java
protected BasePage() {
    this.driver = DriverManager.getDriver();
    this.wait = new WebDriverWait(
            driver,
            Duration.ofSeconds(ConfigManager.getInt("explicit.wait.seconds", 20)));
    PageFactory.initElements(
            new AppiumFieldDecorator(
                    driver,
                    Duration.ofSeconds(ConfigManager.getInt("implicit.wait.seconds", 10))),
            this);
}
```

Why this matters:
- `AppiumFieldDecorator` is what makes `@AndroidFindBy`/`@iOSXCUITFindBy` annotations resolve.
- Every subclass gets element injection "for free" simply by extending `BasePage` and
  having a no-arg flow that reaches `super()`.

### Mandatory: masking on every log/report line
`typeText` already logs the value through `MaskingUtil.mask(...)`. When you log anything
that *could* contain a PAN, CVV, OTP, account number, IBAN, or token, wrap it:

```java
log.info("Submitting transfer to {}", MaskingUtil.mask(beneficiaryRef));
```

Never `System.out.println`. Never `Thread.sleep`. Use the wait helpers.

---

## 4. Locators: `@AndroidFindBy` / `@iOSXCUITFindBy` per platform

Locators are declared as **annotated fields on the page/component**, with **both**
platforms supplied so one class works on Android and iOS. This is the primary mechanism.

```java
public class LoginPage extends BasePage {

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/input_username")
    @iOSXCUITFindBy(accessibility = "login-username-field")
    private WebElement usernameField;

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/btn_login")
    @iOSXCUITFindBy(accessibility = "login-submit-button")
    private WebElement loginButton;
}
```

Conventions:
- **Always provide both** `@AndroidFindBy` and `@iOSXCUITFindBy`. A single-platform field is
  a bug unless the screen is genuinely platform-exclusive.
- **Android**: prefer `id` (full `app.package:id/...` resource id) or
  `@AndroidFindBy(accessibility = "...")` for `content-desc`. Use
  `@AndroidFindBy(uiAutomator = "new UiSelector()...")` only when an id is unavailable.
- **iOS**: prefer `@iOSXCUITFindBy(accessibility = "...")` (the accessibility identifier).
  Fall back to `@iOSXCUITFindBy(iOSNsPredicate = "...")` or `className` for system controls.
- Keep the Android id and iOS accessibility id **mirrored** with the JSON files under
  `src/test/resources/locators/login-<platform>.json` so the externalized copies stay honest.
- Fields are `private WebElement`. For lists use `private List<WebElement>`.

The imports for annotated fields:

```java
import io.appium.java_client.pagefactory.AndroidFindBy;
import io.appium.java_client.pagefactory.iOSXCUITFindBy;
import org.openqa.selenium.WebElement;
```

### Escape hatch: `LocatorRepository` for dynamic/externalized locators
When a locator is dynamic, environment-overridable, or must live outside compiled
annotations, read it from `core.locators.LocatorRepository`:

```java
String selector = LocatorRepository.get("login", "usernameField"); // <page>-<platform>.json
```

`LocatorRepository.get(page, key)` loads `src/test/resources/locators/<page>-<platform>.json`
with Jackson (resolving platform via `Platform.current()`) and returns the raw selector
string. Turning it into a `By` and finding it is still done *inside BasePage* (e.g. via a
`waitForVisible(By)` overload with `AppiumBy.id(...)` / `AppiumBy.accessibilityId(...)`),
never in the page object. Annotations are the default; this is the exception.

---

## 5. Page objects: fluent, masked, no raw driver

Pages model a screen and expose **intent-revealing methods** that return the next page
(fluent navigation), delegating all UI work to `BasePage` helpers.

Page/component API contract used across the suite:

```java
// LoginPage
void enterUsername(String); void enterPassword(String); void tapLogin();
DashboardPage loginWithOtp(String user, String pass, OtpProvider otp);
DashboardPage loginWithBiometric();
boolean isLoaded();

// DashboardPage
boolean isLoaded(); String getMaskedBalance();
BottomNavComponent bottomNav(); TransferPage openTransfer();

// TransferPage
TransferPage selectBeneficiary(String name); TransferPage enterAmount(String amount);
boolean confirmWithOtp(OtpProvider otp); boolean isTransferSuccessful();
```

Example following every convention (annotated fields, `BasePage` helpers, masking,
composed components, fluent return, an `isLoaded()` health-check element):

```java
package com.fintech.qa.pages;

import com.fintech.qa.components.OtpInputComponent;
import com.fintech.qa.core.base.BasePage;
import com.fintech.qa.core.security.OtpProvider;
import io.appium.java_client.pagefactory.AndroidFindBy;
import io.appium.java_client.pagefactory.iOSXCUITFindBy;
import org.openqa.selenium.WebElement;

public class LoginPage extends BasePage {

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/input_username")
    @iOSXCUITFindBy(accessibility = "login-username-field")
    private WebElement usernameField;

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/input_password")
    @iOSXCUITFindBy(accessibility = "login-password-field")
    private WebElement passwordField;

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/login_root")
    @iOSXCUITFindBy(accessibility = "login-root")
    private WebElement loginRoot; // unique to this screen -> drives isLoaded()

    // Reusable widgets are COMPOSED as component objects, not re-located here.
    private final OtpInputComponent otpInput = new OtpInputComponent();

    public void enterUsername(String username) {
        log.info("Entering username on login page");
        typeText(usernameField, username);            // typeText masks the value
    }

    public void enterPassword(String password) {
        log.info("Entering password on login page");  // password is env-sourced, never literal
        typeText(passwordField, password);
    }

    public DashboardPage loginWithOtp(String user, String pass, OtpProvider otp) {
        enterUsername(user);
        enterPassword(pass);
        tap(/* loginButton field */ usernameField);   // illustrative; tap the real button field
        otpInput.enterOtp(otp.fetchOtp(user));         // OTP via provider, never real SMS
        return new DashboardPage();                    // fluent -> next screen
    }

    public boolean isLoaded() {
        return isDisplayed(loginRoot);                 // absence-safe probe, no throw
    }
}
```

Page rules:
- Public methods get **Javadoc** (this is a public framework API).
- Return the destination page object for navigation transitions; return `boolean`/`String`
  for state queries.
- **Never** hardcode credentials/OTPs. Passwords come from env via `ConfigManager`; OTPs
  come from an injected `OtpProvider`; biometrics from `BiometricHelper`.
- Balances/PANs are exposed via **masked** getters (e.g. `getMaskedBalance()` returns
  already-masked text — do not expose raw balances).

---

## 6. Component objects: reusable widgets, same rules

A component is a `BasePage` subclass that wraps a widget recurring across screens.
Construct it where it is used (e.g. as a field on a page, or `new BottomNavComponent()`
in a step). It carries its own annotated locators and returns page objects when it
triggers navigation.

```java
package com.fintech.qa.components;

import com.fintech.qa.core.base.BasePage;
import com.fintech.qa.pages.DashboardPage;
import com.fintech.qa.pages.TransferPage;
import io.appium.java_client.pagefactory.AndroidFindBy;
import io.appium.java_client.pagefactory.iOSXCUITFindBy;
import org.openqa.selenium.WebElement;

public class BottomNavComponent extends BasePage {

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/nav_home")
    @iOSXCUITFindBy(accessibility = "nav-home")
    private WebElement homeTab;

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/nav_transfers")
    @iOSXCUITFindBy(accessibility = "nav-transfers")
    private WebElement transfersTab;

    public DashboardPage goToHome()      { tap(homeTab);      return new DashboardPage(); }
    public TransferPage  goToTransfers() { tap(transfersTab); return new TransferPage();  }
    public void          goToProfile()   { tap(/* profileTab */ homeTab); } // no page object
}
```

Component API contract:

```java
// BottomNavComponent
DashboardPage goToHome(); TransferPage goToTransfers(); void goToProfile();
// OtpInputComponent
void enterOtp(String otp);                 // delegates to typeText -> masked
// BiometricPromptComponent
boolean isShown(); void approveWithMatch(); void denyWithNonMatch();
// ToastComponent
String getMessage(); boolean isShown();
```

Sensitive component example — the OTP grid never logs the code in clear text:

```java
public class OtpInputComponent extends BasePage {

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/input_otp")
    @iOSXCUITFindBy(accessibility = "login-otp-field")
    private WebElement otpField;

    public void enterOtp(String otp) {
        typeText(otpField, otp);            // masked by typeText
        log.info("Entered OTP code (masked)");
    }
}
```

---

## 7. Driver plumbing: config-driven, ThreadLocal, factory + manager

Two collaborators, with a clean split of responsibility:

**`DriverManager`** — thread confinement only. Backed by `ThreadLocal<AppiumDriver>` so
parallel scenarios never share a session.

```java
static AppiumDriver getDriver();          // current thread's driver (BasePage uses this)
static void         setDriver(AppiumDriver driver);
static void         quitDriver();          // quit + remove from ThreadLocal
static Platform     getPlatform();
```

**`DriverFactory`** — builds a session from config; does **not** register it (the caller,
usually `CucumberHooks`, calls `DriverManager.setDriver(...)`). This keeps thread
ownership explicit.

```java
public static AppiumDriver createDriver() {
    Platform platform = Platform.current();                 // reads config "platform"
    URL serverUrl = URI.create(
            ConfigManager.get("appium.server.url", "http://127.0.0.1:4723")).toURL();
    return switch (platform) {                              // build URL via URI.create(...).toURL()
        case ANDROID -> new AndroidDriver(serverUrl, CapabilitiesBuilder.androidOptions());
        case IOS     -> new IOSDriver(serverUrl, CapabilitiesBuilder.iosOptions());
    };
}
```

**`Platform`** — `enum ANDROID, IOS`; `static Platform current()` reads
`ConfigManager.get("platform","android")`. Switch on `Platform`, never on a raw string.

**`CapabilitiesBuilder`** — `static UiAutomator2Options androidOptions()` /
`static XCUITestOptions iosOptions()`; reads caps from config keys
(`android.deviceName`, `android.platformVersion`, `app.package`, etc.) and includes
biometric/enrollment-friendly flags. Everything is config-driven; no caps are hardcoded.

Driver imports (java-client 9.x + Selenium 4):

```java
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.ios.options.XCUITestOptions;
```

The thread-confinement chain, end to end:

```
CucumberHooks @Before  ->  DriverFactory.createDriver()  ->  DriverManager.setDriver(d)
BasePage()             ->  DriverManager.getDriver()       (every page/component reads the SAME thread's d)
CucumberHooks @After   ->  DriverManager.quitDriver()
```

Because `BasePage` resolves the driver via `DriverManager.getDriver()`, page objects are
automatically thread-safe under parallel execution — **never** pass an `AppiumDriver`
into a page constructor or store one statically.

---

## 8. Configuration access (no secrets, env-driven)

Everything tunable flows through `core.config.ConfigManager`:

```java
ConfigManager.get("appium.server.url", "http://127.0.0.1:4723");
ConfigManager.getInt("explicit.wait.seconds", 20);
ConfigManager.getBoolean("appium.autoGrantPermissions", true);
```

Resolution order (later wins): `src/test/resources/config/config.properties` → JVM system
properties (`-Dkey=value`) → environment variables (`KEY` mapped to `UPPER_SNAKE_CASE`).

- `config.properties` holds **non-secret defaults only** (platform, server URL, device
  names, wait timeouts).
- Secrets (`TEST_USER_PASSWORD`, `OTP_API_TOKEN`, `DEVICE_FARM_TOKEN`) come **only from
  env vars** — never a literal in code or a value in `config.properties`.

---

## 9. Checklist before committing a page/component/driver change

- [ ] Class extends `BasePage` (pages and components both).
- [ ] All UI work goes through `BasePage` helpers — **no** `driver.findElement`,
      `.click()`, `.sendKeys()`, or `Actions` in the page/component/step.
- [ ] Every locator field has **both** `@AndroidFindBy` and `@iOSXCUITFindBy`.
- [ ] Android ids / iOS accessibility ids mirror `src/test/resources/locators/<page>-<platform>.json`.
- [ ] Navigation methods return the destination page object (fluent).
- [ ] Public framework methods carry Javadoc.
- [ ] No `System.out`, no `Thread.sleep` — slf4j `log` + explicit waits only.
- [ ] Any log/report text that may contain PAN/CVV/OTP/account/IBAN/token is wrapped in
      `MaskingUtil.mask(...)` (or goes through `typeText`, which already masks).
- [ ] No hardcoded credentials/OTPs; passwords via env, OTP via `OtpProvider`, biometric
      via `BiometricHelper`.
- [ ] Driver obtained via `DriverManager.getDriver()`; never stored statically or passed in.
- [ ] Config read via `ConfigManager` with sensible defaults; secrets only from env.
```