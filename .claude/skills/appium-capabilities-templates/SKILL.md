---
name: appium-capabilities-templates
description: Use when creating or editing Appium capabilities - UiAutomator2Options/XCUITestOptions for emulator, real device, or BrowserStack, mapped to config.properties, with biometric flags and env secrets.
---

# Appium Capabilities Templates (fintech wallet QA)

Authoritative guidance for building Appium driver options in this repo. Use it whenever you
add a new device target, wire a capability, change `config.properties`, or touch a
`src/test/resources/capabilities/*.json` template.

The single source of truth for **runtime** capabilities is
`com.fintech.qa.core.driver.CapabilitiesBuilder` (main sources). The JSON files under
`src/test/resources/capabilities/` are **documentation/templates** (Appium 2 W3C shape) that
mirror what the builder produces — they are NOT loaded by the driver. Keep the two in sync.

Versions in play (do not change, do not add Selenium — it is transitive via java-client):
`io.appium:java-client:9.3.0`, JUnit Platform Suite `1.11.3`, Cucumber `7.20.1`, Java 17.

## Golden rules

1. **Secrets only from env.** Never put a password, OTP token, or device-farm key in
   `config.properties` or any `capabilities/*.json`. Read them through `ConfigManager.get(...)`
   which resolves env vars last (`KEY` -> `UPPER_SNAKE_CASE`). Required env vars:
   `TEST_USER_PASSWORD`, `OTP_API_TOKEN`, `DEVICE_FARM_TOKEN`, `BROWSERSTACK_USERNAME`.
2. **Read every cap from `ConfigManager`** with a non-secret default — never hardcode in the
   builder. This is what lets `-Dplatform=ios` / env overrides work without recompiling.
3. **Biometric-friendly flags stay on** so `BiometricHelper` can drive fingerprint / Face ID
   simulation at runtime (Android `mobile: fingerprint`, iOS `mobile: sendBiometricMatch`).
4. **No real PII / PAN / bundle ids.** Use the synthetic placeholders
   (`com.fintech.wallet.sample`, `bs://SYNTHETIC_PLACEHOLDER_APP_ID`).
5. The driver always builds the server URL with `URI.create(url).toURL()` (see
   `DriverFactory.resolveServerUrl()`) against `appium.server.url`
   (default `http://127.0.0.1:4723`).

## Config key -> capability map

These are the keys `CapabilitiesBuilder` reads. Add new keys here when you extend it.

| config.properties key            | Android (UiAutomator2)        | iOS (XCUITest)                 | Default      |
|----------------------------------|-------------------------------|--------------------------------|--------------|
| `platform`                       | selects builder               | selects builder                | `android`    |
| `appium.server.url`              | server endpoint               | server endpoint                | `http://127.0.0.1:4723` |
| `android.automationName`         | `automationName`              | -                              | `UiAutomator2` |
| `android.deviceName`             | `deviceName`                  | -                              | `Android Emulator` |
| `android.platformVersion`        | `platformVersion`             | -                              | (unset)      |
| `ios.automationName`             | -                             | `automationName`               | `XCUITest`   |
| `ios.deviceName`                 | -                             | `deviceName`                   | `iPhone 15`  |
| `ios.platformVersion`            | -                             | `platformVersion`              | (unset)      |
| `app.path`                       | `app` (artifact, if non-blank)| `app` (artifact, if non-blank) | (unset)      |
| `app.package` / `app.activity`   | `appPackage` / `appActivity`  | -                              | synthetic    |
| `app.bundleId`                   | -                             | `bundleId`                     | synthetic    |
| `appium.newCommandTimeout`       | `newCommandTimeout`           | `newCommandTimeout`            | `120`        |
| `appium.autoGrantPermissions`    | `autoGrantPermissions`        | -                              | `true`       |
| `appium.autoAcceptAlerts`        | -                             | `autoAcceptAlerts`             | `false`      |
| `appium.noReset` / `appium.fullReset` | `noReset` / `fullReset`  | `noReset` / `fullReset`        | `false`      |
| `ios.wdaLaunchTimeout`           | -                             | `wdaLaunchTimeout` (ms)        | `120000`     |
| `ios.wdaConnectionTimeout`       | -                             | `wdaConnectionTimeout` (ms)    | `120000`     |

`app.path` wins over package/bundle: if set non-blank the builder calls `setApp(...)` and skips
`appPackage`/`appActivity`/`bundleId`. Leave `app.path` blank to drive an already-installed app.

## Android template (UiAutomator2Options)

This mirrors `CapabilitiesBuilder.androidOptions()`. Every value comes from `ConfigManager`.

```java
import io.appium.java_client.android.options.UiAutomator2Options;
import com.fintech.qa.core.config.ConfigManager;
import java.time.Duration;

UiAutomator2Options options = new UiAutomator2Options();
options.setPlatformName("Android");
options.setAutomationName(ConfigManager.get("android.automationName", "UiAutomator2"));
options.setDeviceName(ConfigManager.get("android.deviceName", "Android Emulator"));

String pv = ConfigManager.get("android.platformVersion");
if (pv != null) options.setPlatformVersion(pv);

String appPath = ConfigManager.get("app.path");
if (appPath != null && !appPath.isBlank()) {
    options.setApp(appPath);                       // installer artifact / URL
} else {
    options.setAppPackage(ConfigManager.get("app.package"));
    options.setAppActivity(ConfigManager.get("app.activity"));
}

options.setNewCommandTimeout(Duration.ofSeconds(ConfigManager.getInt("appium.newCommandTimeout", 120)));
options.setAutoGrantPermissions(ConfigManager.getBoolean("appium.autoGrantPermissions", true));
options.setNoReset(ConfigManager.getBoolean("appium.noReset", false));
options.setFullReset(ConfigManager.getBoolean("appium.fullReset", false));

// Hybrid screens (SSL-pinning / 3DS / web-view OTP) need real web-view pages + screenshots.
options.setEnsureWebviewsHavePages(true);
options.setNativeWebScreenshot(true);

// Deterministic waits + biometric enrollment friendliness (no Thread.sleep anywhere).
options.setCapability("appium:disableWindowAnimation", true);   // stable explicit waits
options.setCapability("appium:skipDeviceInitialization", false);
options.setCapability("appium:enableMultiWindows", true);       // keep AVD fingerprint-friendly
```

Matching template file: `src/test/resources/capabilities/android-emulator.json`.

## iOS template (XCUITestOptions)

This mirrors `CapabilitiesBuilder.iosOptions()`.

```java
import io.appium.java_client.ios.options.XCUITestOptions;
import com.fintech.qa.core.config.ConfigManager;
import java.time.Duration;

XCUITestOptions options = new XCUITestOptions();
options.setPlatformName("iOS");
options.setAutomationName(ConfigManager.get("ios.automationName", "XCUITest"));
options.setDeviceName(ConfigManager.get("ios.deviceName", "iPhone 15"));

String pv = ConfigManager.get("ios.platformVersion");
if (pv != null) options.setPlatformVersion(pv);

String appPath = ConfigManager.get("app.path");
if (appPath != null && !appPath.isBlank()) {
    options.setApp(appPath);
} else {
    options.setBundleId(ConfigManager.get("app.bundleId"));
}

options.setNewCommandTimeout(Duration.ofSeconds(ConfigManager.getInt("appium.newCommandTimeout", 120)));
options.setNoReset(ConfigManager.getBoolean("appium.noReset", false));
options.setFullReset(ConfigManager.getBoolean("appium.fullReset", false));
// Leave alerts to explicit security/biometric steps (root prompt, SSL-pin UX, app backgrounding).
options.setAutoAcceptAlerts(ConfigManager.getBoolean("appium.autoAcceptAlerts", false));

// Face ID / Touch ID simulation friendliness for BiometricHelper.
options.setCapability("appium:allowTouchIdEnroll", true);
options.setCapability("appium:connectHardwareKeyboard", false);
options.setWdaLaunchTimeout(Duration.ofMillis(ConfigManager.getInt("ios.wdaLaunchTimeout", 120_000)));
options.setWdaConnectionTimeout(Duration.ofMillis(ConfigManager.getInt("ios.wdaConnectionTimeout", 120_000)));
```

Matching template file: `src/test/resources/capabilities/ios-simulator.json`.

## Biometric-simulation flags (must stay enabled)

`BiometricHelper` is platform-aware via `DriverManager.getPlatform()` and calls
`executeScript`. The capabilities only have to keep the device enrollable:

- **Android emulator:** `appium:enableMultiWindows=true`; emulator created with a fingerprint
  sensor. Runtime: `executeScript("mobile: fingerprint", Map.of("fingerprintId", 1))`.
  Enroll the print once via `BiometricHelper.enroll()` before `match()` / `nonMatch()`.
- **iOS simulator:** `appium:allowTouchIdEnroll=true`. Runtime:
  `executeScript("mobile: sendBiometricMatch", Map.of("type","faceId","match",true|false))`.
- **Real devices / BrowserStack:** biometric injection is farm-dependent; do not assume it
  works. Gate biometric scenarios with a tag and fall back to OTP (`OtpProvider`) login.

Do not add `appium:noReset=true` globally to "speed up" biometric tests — a reset between
scenarios is what guarantees a clean enrollment state for `enroll()`.

## Local emulator / simulator profile

Use the defaults already in `config.properties`. Run:

```bash
# Android (default platform=android)
mvn test
# iOS
mvn test -Dplatform=ios
```

No secrets needed for `otp.provider=static` local runs (`StaticOtpProvider` -> `otp.static`).

## Real-device profile

Override device coordinates and (optionally) point at a remote/cloud Appium server. Pass the
real device serial / UDID at runtime — never commit it.

```bash
# Android real device
mvn test \
  -Dandroid.deviceName=RZ8N12345AB \
  -Dandroid.platformVersion=14 \
  -Dappium.autoGrantPermissions=true

# iOS real device (also set xcodeOrgId/updatedWDABundleId via -D… if your WDA signing needs it)
mvn test -Dplatform=ios \
  -Dios.deviceName="My iPhone" \
  -Dios.platformVersion=17.5
```

If you must add a real-device-only cap (e.g. `appium:udid`, `appium:xcodeOrgId`,
`appium:updatedWDABundleId`), read it through `ConfigManager.get(...)` with a default and only
set it when present — mirror the `platformVersion`/`app.path` null-guard pattern. Signing team
ids are not secrets, but provisioning passwords / keystores are: keep those in env, never in
`config.properties`.

## BrowserStack profile

Template: `src/test/resources/capabilities/browserstack.json`. Credentials are placeholders
(`${BROWSERSTACK_USERNAME}`, `${DEVICE_FARM_TOKEN}`) resolved from env at runtime — the literal
keys never live in the file. To target BrowserStack, point the server URL at the hub and feed
the `bstack:options` block from env:

```bash
export BROWSERSTACK_USERNAME=...        # not a secret, but injected from env for parity
export DEVICE_FARM_TOKEN=...            # SECRET access key — env only

mvn test \
  -Dappium.server.url="https://hub.browserstack.com/wd/hub" \
  -Dandroid.deviceName="Google Pixel 8" \
  -Dandroid.platformVersion=14.0
```

When you extend `CapabilitiesBuilder` for BrowserStack, set the `bstack:options` map and read
the access key from env via `ConfigManager`:

```java
java.util.Map<String, Object> bstack = new java.util.HashMap<>();
bstack.put("userName", ConfigManager.get("browserstack.userName"));   // env: BROWSERSTACK_USERNAME
bstack.put("accessKey", ConfigManager.get("device.farm.token"));      // env: DEVICE_FARM_TOKEN (SECRET)
bstack.put("projectName", ConfigManager.get("browserstack.projectName", "fintech-mobile-qa"));
bstack.put("buildName", ConfigManager.get("browserstack.buildName", "fintech-wallet-regression"));
bstack.put("sessionName", ConfigManager.get("browserstack.sessionName", "appium-cucumber-suite"));
bstack.put("appiumVersion", ConfigManager.get("browserstack.appiumVersion", "2.11.0"));
options.setCapability("bstack:options", bstack);
// Upload your synthetic build first; reference the bs://… id (placeholder, never a real artifact id).
options.setApp(ConfigManager.get("app.path", "bs://SYNTHETIC_PLACEHOLDER_APP_ID"));
```

Assert before pushing: a `grep` for the token names finds only `ConfigManager.get(...)` and
env references, never a literal value.

## When you add a capability — checklist

1. Add the config key + non-secret default to `config.properties` (table above) — unless it is
   a secret, in which case document only the env var name, no default value.
2. Read it in `CapabilitiesBuilder` via `ConfigManager.get/getInt/getBoolean` with a null-guard
   for optional values (follow the `platformVersion` / `app.path` patterns).
3. Update the matching `capabilities/*.json` template so docs stay truthful.
4. Keep biometric flags intact; do not introduce `Thread.sleep` or raw `findElement`.
5. Confirm it compiles: `mvn -q -DskipTests compile`.
