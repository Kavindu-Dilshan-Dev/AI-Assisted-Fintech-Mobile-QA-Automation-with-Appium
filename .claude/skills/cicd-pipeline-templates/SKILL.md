---
name: cicd-pipeline-templates
description: Use when wiring CI/CD for the fintech-mobile-qa Appium+Cucumber suite - GitHub Actions (Android emulator, device-farm), publishing Extent/cucumber/junit artifacts, env-only secrets, or a Jenkinsfile.
---

# CI/CD Pipeline Templates — fintech-mobile-qa

Authoritative templates for running this Maven (Java 17) Appium + Cucumber + JUnit
Platform suite in CI. Use these when someone asks to "add CI", "run the mobile tests
in GitHub Actions", "spin up an emulator in the pipeline", "publish the Extent report",
"run on BrowserStack / a device farm", or "give me a Jenkinsfile".

## Non-negotiable rules (fintech)

These mirror the framework's cross-cutting contract and MUST hold in every pipeline:

- **Secrets only via env / CI secret store.** Never hardcode a password, OTP API token,
  or device-farm key in YAML. Inject through `${{ secrets.* }}` → `env:`. The framework's
  `ConfigManager` resolves order: `config.properties` → JVM `-D` → environment
  (`KEY` → `UPPER_SNAKE_CASE`). So `TEST_USER_PASSWORD`, `OTP_API_TOKEN`,
  `DEVICE_FARM_TOKEN` etc. become available to `ConfigManager.get(...)` automatically.
- **No real PII/PAN.** The suite uses Luhn-valid fake cards and masks PAN/CVV/OTP/account/
  token via `MaskingUtil` before any log/report line, so CI logs are already safe — but
  never `cat`/`echo` a secret env var in a step.
- **OTP via `OtpProvider`.** In CI default to the static provider
  (`-Dotp.provider=static`) unless a reachable test backend is configured via
  `OTP_API_URL` + `OTP_API_TOKEN`.
- **Always publish artifacts even on failure** (`if: always()`), because the Extent report
  and screenshots are the primary triage surface.

## What the build produces (exact paths)

Confirmed from `pom.xml`, `src/test/resources/extent.properties`, and
`src/test/resources/junit-platform.properties`:

| Output | Path | Produced by |
| --- | --- | --- |
| Extent HTML (Spark) | `target/extent-report/Spark.html` | grasshopper adapter (`extent.properties`) |
| Cucumber JSON | `target/cucumber.json` | `cucumber.plugin=json:target/cucumber.json` |
| Cucumber JUnit XML | `target/cucumber.xml` | `cucumber.plugin=junit:target/cucumber.xml` |
| Surefire reports | `target/surefire-reports/` | maven-surefire-plugin 3.5.1 |
| Screenshots | `target/screenshots/` | `ScreenshotUtil.capture(...)` |

Build/test command (single module, runner is `CucumberTestRunner`):

```bash
# Android (default profile sets platform=android)
mvn -B -ntp clean test
# iOS
mvn -B -ntp clean test -Dplatform=ios
# Pin the OTP + Appium server explicitly in CI
mvn -B -ntp test -Dplatform=android -Dotp.provider=static -Dappium.server.url=http://127.0.0.1:4723
```

`-B` (batch) and `-ntp` (no transfer progress) keep CI logs clean.

## Required GitHub repository secrets

Create these under **Settings → Secrets and variables → Actions**. None belong in YAML:

| Secret | Used by | Notes |
| --- | --- | --- |
| `TEST_USER_PASSWORD` | `ConfigManager.get("test.user.password")` | login fixtures |
| `OTP_API_TOKEN` | `TestApiOtpProvider` (test mailbox) | only if `otp.provider=api` |
| `OTP_API_URL` | `TestApiOtpProvider` | non-secret URL ok as repo *variable* too |
| `BROWSERSTACK_USERNAME` | device-farm variant | |
| `BROWSERSTACK_ACCESS_KEY` | device-farm variant | |
| `DEVICE_FARM_TOKEN` | generic device farm | alternative to BrowserStack |

---

## Template 1 — GitHub Actions: Android emulator (primary)

Write to `.github/workflows/android-emulator.yml`. Uses
`reactivecircus/android-emulator-runner` which boots a hardware-accelerated emulator on
the macOS runner (KVM is available on `ubuntu-latest` too for API levels that support it;
macOS is the most reliable for app-launch + biometric enrollment).

```yaml
name: android-emulator-tests

on:
  push:
    branches: [main, develop]
  pull_request:
  workflow_dispatch:
    inputs:
      api-level:
        description: "Android API level"
        default: "34"

concurrency:
  group: android-${{ github.ref }}
  cancel-in-progress: true

jobs:
  android-emulator:
    # macOS runners expose nested virtualization for the emulator.
    runs-on: macos-13
    timeout-minutes: 60
    strategy:
      fail-fast: false
      matrix:
        api-level: [34]

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17 (Temurin)
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
          cache: maven            # caches ~/.m2 keyed on pom.xml

      - name: Set up Node (for Appium)
        uses: actions/setup-node@v4
        with:
          node-version: "20"

      - name: Install Appium 2 + UiAutomator2 driver
        run: |
          npm install -g appium@2
          appium driver install uiautomator2
          appium --version

      - name: Cache AVD snapshot
        uses: actions/cache@v4
        id: avd-cache
        with:
          path: |
            ~/.android/avd/*
            ~/.android/adb*
          key: avd-${{ matrix.api-level }}

      - name: Start Appium server (background)
        run: |
          appium --address 127.0.0.1 --port 4723 \
            --log-timestamp --log appium.log &
          # Wait for the server /status endpoint instead of sleeping blindly.
          for i in $(seq 1 30); do
            curl -sf http://127.0.0.1:4723/status && break
            sleep 2
          done

      - name: Run tests on emulator
        uses: reactivecircus/android-emulator-runner@v2
        env:
          # ---- Secrets injected as env; ConfigManager maps to UPPER_SNAKE_CASE ----
          TEST_USER_PASSWORD: ${{ secrets.TEST_USER_PASSWORD }}
          OTP_API_TOKEN: ${{ secrets.OTP_API_TOKEN }}
          OTP_API_URL: ${{ vars.OTP_API_URL }}
        with:
          api-level: ${{ matrix.api-level }}
          target: google_apis
          arch: x86_64
          profile: pixel_6
          ram-size: 4096M
          # Headless, animations off, biometric/enrollment-friendly boot.
          emulator-options: -no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none
          disable-animations: true
          script: |
            adb devices
            # Pre-enroll a fingerprint so BiometricHelper.match() works on the AVD.
            adb -e emu finger touch 1 || true
            mvn -B -ntp clean test \
              -Dplatform=android \
              -Dotp.provider=static \
              -Dappium.server.url=http://127.0.0.1:4723

      - name: Publish Extent HTML report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: extent-report-api${{ matrix.api-level }}
          path: target/extent-report/
          if-no-files-found: warn

      - name: Publish Cucumber + JUnit results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: cucumber-results-api${{ matrix.api-level }}
          path: |
            target/cucumber.json
            target/cucumber.xml
            target/surefire-reports/

      - name: Publish screenshots
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: screenshots-api${{ matrix.api-level }}
          path: target/screenshots/
          if-no-files-found: ignore

      - name: Publish Appium server log
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: appium-log-api${{ matrix.api-level }}
          path: appium.log
          if-no-files-found: ignore

      - name: Surface JUnit results in PR checks
        if: always()
        uses: dorny/test-reporter@v1
        with:
          name: cucumber (api ${{ matrix.api-level }})
          path: target/cucumber.xml
          reporter: java-junit
          fail-on-error: false
```

### Why these choices

- `cache: maven` on `setup-java` keys `~/.m2` on `pom.xml`, so the pinned versions
  (appium 9.3.0, cucumber 7.20.1, extentreports 5.1.2, etc.) download once.
- The emulator-runner `script:` is where `mvn test` runs **after** the AVD is booted —
  Appium connects to the already-running device via the local server on `:4723`.
- `adb -e emu finger touch 1` matches the Android branch of `BiometricHelper`
  (`executeScript("mobile: fingerprint", Map.of("fingerprintId", 1))`).
- `if: always()` on every upload guarantees the Extent report and screenshots survive a
  red build for triage.

---

## Template 2 — GitHub Actions: BrowserStack / device-farm variant

Write to `.github/workflows/device-farm.yml`. No local emulator — the suite points
Appium at the BrowserStack hub URL. Secrets stay in the secret store; the hub URL is
built from them at runtime and never printed.

```yaml
name: device-farm-tests

on:
  workflow_dispatch:
  schedule:
    - cron: "0 2 * * *"   # nightly regression on real devices

jobs:
  browserstack:
    runs-on: ubuntu-latest
    timeout-minutes: 45
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17 (Temurin)
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
          cache: maven

      - name: Run suite against BrowserStack
        env:
          BROWSERSTACK_USERNAME: ${{ secrets.BROWSERSTACK_USERNAME }}
          BROWSERSTACK_ACCESS_KEY: ${{ secrets.BROWSERSTACK_ACCESS_KEY }}
          TEST_USER_PASSWORD: ${{ secrets.TEST_USER_PASSWORD }}
          OTP_API_TOKEN: ${{ secrets.OTP_API_TOKEN }}
          OTP_API_URL: ${{ vars.OTP_API_URL }}
        run: |
          # Build the hub URL from secrets WITHOUT echoing them.
          HUB="https://${BROWSERSTACK_USERNAME}:${BROWSERSTACK_ACCESS_KEY}@hub-cloud.browserstack.com/wd/hub"
          # Mask the value so it never leaks into logs even if a step prints it.
          echo "::add-mask::${BROWSERSTACK_ACCESS_KEY}"
          mvn -B -ntp clean test \
            -Dplatform=android \
            -Dotp.provider=static \
            -Dappium.server.url="${HUB}"
        # ConfigManager.get("appium.server.url") receives the hub via -D;
        # CapabilitiesBuilder still reads device/app caps from config keys.

      - name: Publish Extent + Cucumber + JUnit artifacts
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: device-farm-results
          path: |
            target/extent-report/
            target/cucumber.json
            target/cucumber.xml
            target/surefire-reports/
            target/screenshots/
          if-no-files-found: warn
```

For a **generic device farm** (single token instead of user/key), use
`DEVICE_FARM_TOKEN` and pass it through config keys your `CapabilitiesBuilder` reads —
e.g. add `-Dappium.server.url=${{ secrets.DEVICE_FARM_URL }}` and set a custom header /
cap via a config key sourced from `DEVICE_FARM_TOKEN` (env → `ConfigManager`). Never
embed the token in the URL literal in YAML.

> Tip: BrowserStack-specific app upload and `bstack:options` caps belong in
> `CapabilitiesBuilder.androidOptions()` / `iosOptions()` reading config keys, NOT in the
> workflow. The pipeline only supplies the hub URL + credentials via env.

---

## Secret injection cheat-sheet

```yaml
# 1. Define once in the job/step env, sourced from the secret store:
env:
  TEST_USER_PASSWORD: ${{ secrets.TEST_USER_PASSWORD }}

# 2. ConfigManager resolves it automatically:
#    key "test.user.password"  ->  env "TEST_USER_PASSWORD"  (UPPER_SNAKE_CASE)
#    Resolution order: config.properties -> -Dsystem.prop -> ENV (env wins).

# 3. NEVER do this:
#    run: echo "pwd=${{ secrets.TEST_USER_PASSWORD }}"   # leaks the secret
#    Use ::add-mask:: if a value must transit a shell variable.
```

`config.properties` (`src/test/resources/config/config.properties`) holds only
**non-secret defaults** (platform, waits, appium url, otp.static for local). CI overrides
behavior with `-D` flags and supplies secrets purely through env.

---

## Jenkinsfile alternative (brief)

For teams on Jenkins, a declarative `Jenkinsfile` at repo root mirrors the same contract:
JDK 17 tool, `withCredentials` for secrets (the Jenkins analogue of GH secrets), `mvn test`,
then archive the exact same artifact paths. `junit` step consumes `target/cucumber.xml`;
HTML Publisher plugin serves `target/extent-report/Spark.html`.

```groovy
pipeline {
  agent any
  tools { jdk 'jdk-17'; maven 'maven-3.9' }
  options { timeout(time: 60, unit: 'MINUTES'); disableConcurrentBuilds() }
  environment {
    OTP_API_URL = 'https://test-mailbox.internal/otp'
  }
  stages {
    stage('Test') {
      steps {
        // Secrets from Jenkins Credentials store — never inline in the Jenkinsfile.
        withCredentials([
          string(credentialsId: 'test-user-password', variable: 'TEST_USER_PASSWORD'),
          string(credentialsId: 'otp-api-token',       variable: 'OTP_API_TOKEN'),
          usernamePassword(credentialsId: 'browserstack',
                           usernameVariable: 'BROWSERSTACK_USERNAME',
                           passwordVariable: 'BROWSERSTACK_ACCESS_KEY')
        ]) {
          sh '''
            mvn -B -ntp clean test \
              -Dplatform=android \
              -Dotp.provider=static \
              -Dappium.server.url=http://127.0.0.1:4723
          '''
        }
      }
    }
  }
  post {
    always {
      junit testResults: 'target/cucumber.xml', allowEmptyResults: true
      archiveArtifacts artifacts: 'target/cucumber.json, target/surefire-reports/**, target/screenshots/**',
                       allowEmptyArchive: true
      publishHTML(target: [
        reportDir: 'target/extent-report',
        reportFiles: 'Spark.html',
        reportName: 'Extent Report',
        keepAll: true, alwaysLinkToLastBuild: true, allowMissing: true
      ])
    }
  }
}
```

Jenkins needs an emulator/device on the agent (or a device-farm hub URL) just like GitHub
Actions — the emulator boot is environment-specific and lives in the agent setup, not the
pipeline logic.

---

## Compliance / tag filtering in CI

Cucumber tags let CI target compliance suites. Pass through surefire to the cucumber engine:

```bash
# Run only PCI-DSS-tagged scenarios in a dedicated compliance job
mvn -B -ntp test -Dplatform=android \
  -Dcucumber.filter.tags="@PCI-DSS-* or @REQ-*"
```

Run security negative scenarios (session timeout, root/jailbreak prompt, SSL-pinning
failure, app-backgrounding) as their own gated job by tagging them and filtering the same
way, so a failure there blocks the merge independently of the happy-path regression.

## Checklist when adding/editing a pipeline

- [ ] JDK 17 (Temurin) + `cache: maven`.
- [ ] Appium server reachable at the URL passed via `-Dappium.server.url`.
- [ ] Emulator booted (or hub URL set) BEFORE `mvn test`.
- [ ] Fingerprint pre-enrolled on AVD for `BiometricHelper` (`adb -e emu finger touch 1`).
- [ ] OTP provider explicit (`-Dotp.provider=static` in CI by default).
- [ ] Every secret comes from `${{ secrets.* }}`/credentials → env. Zero secrets in YAML.
- [ ] Artifacts uploaded with `if: always()`: `target/extent-report/`,
      `target/cucumber.json`, `target/cucumber.xml`, `target/surefire-reports/`,
      `target/screenshots/`.
- [ ] No step echoes a secret; use `::add-mask::` for any secret transiting a shell var.
```
