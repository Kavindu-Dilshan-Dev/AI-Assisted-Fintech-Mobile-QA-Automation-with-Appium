---
name: self-healing-locator-strategy
description: Use when adding/fixing Appium locators or triaging NoSuchElement/Stale/Timeout flake - locator priority (a11y id->id->XPath->AI visual), retry/backoff, LocatorRepository, healer-vs-human escalation.
---

# Self-Healing Locator Strategy (fintech wallet QA)

Authoritative guidance for choosing, ordering, externalizing, and healing locators in this
repo. Use it whenever you add a `@AndroidFindBy`/`@iOSXCUITFindBy` to a page, edit a
`src/test/resources/locators/<page>-<platform>.json` file, or triage a failing scenario that
throws `NoSuchElementException`, `StaleElementReferenceException`, or `TimeoutException`.

Versions in play (do not change; Selenium is transitive via java-client — never declare it):
`io.appium:java-client:9.3.0`, `io.cucumber:cucumber-java:7.20.1`, JUnit Platform Suite
`1.11.3`, Java 17. PageFactory injection is wired in
`com.fintech.qa.core.base.BasePage` via `AppiumFieldDecorator`.

## Non-negotiable framework rules (do not break while healing)

1. **Only `BasePage` may call `driver.findElement(...)`.** Pages, components, and step
   definitions interact through `tap`, `typeText`, `waitForVisible(...)`, `isDisplayed`,
   `contentDescription`, etc. A "self-healing" fix that sprinkles `driver.findElement` into a
   page is a regression — keep all element resolution behind the annotation + `BasePage`.
2. **No `Thread.sleep`.** Healing retries use explicit waits (`WebDriverWait` /
   `ExpectedConditions`) sized from `explicit.wait.seconds` (default 20s) and the implicit
   PageFactory timeout `implicit.wait.seconds` (default 10s) — both read via `ConfigManager`.
3. **Mask before you log.** Any diagnostic line that could echo a typed value, OTP, PAN, CVV,
   account number, or token goes through `com.fintech.qa.core.security.MaskingUtil.mask(...)`.
   `BasePage.typeText` and `contentDescription` already do this — match that discipline in any
   new helper or healer note.
4. **No real PII in selectors or fixtures.** Use the synthetic app id
   `com.fintech.wallet.sample` and the existing fake test data; never key a locator off a real
   PAN/account/customer string.

## Locator priority order (strictly highest to lowest)

Always reach for the most stable, semantic, cross-platform locator first. Drop a tier only when
the tier above is genuinely unavailable in the app build.

### Tier 1 — Accessibility id (PREFERRED)

Most stable and the one we also assert for accessibility compliance. Use the **same** id that
`BasePage.contentDescription(...)` can read back, so a11y tests and functional tests reinforce
each other.

```java
// iOS: accessibility identifier. Android: content-desc.
@AndroidFindBy(accessibility = "login-submit-button")
@iOSXCUITFindBy(accessibility = "login-submit-button")
private WebElement loginButton;
```

Cross-platform accessibility ids (`AppiumBy.accessibilityId`) are the only locator that can be
identical on both platforms — push the app team to add them. They double as the content
description our a11y assertions check via `contentDescription(...)`.

### Tier 2 — Platform native id (resource-id on Android / name on iOS)

Stable but platform-specific. This is what the current `LoginPage` uses for Android and is the
shape stored in `login-android.json`.

```java
@AndroidFindBy(id = "com.fintech.wallet.sample:id/btn_login")   // resource-id
@iOSXCUITFindBy(accessibility = "login-submit-button")          // prefer a11y id on iOS
private WebElement loginButton;
```

Rules: always fully-qualify the Android resource-id with the synthetic package
(`com.fintech.wallet.sample:id/...`). Never match on `text=` for primary controls — copy and
localization changes break it.

### Tier 3 — Constrained UiAutomator / predicate / class-chain

When there is no id, use a **scoped, attribute-based** selector — not a positional one.

```java
// Android: UiAutomator scoped to a stable container + a stable attribute.
@AndroidFindBy(uiAutomator =
    "new UiSelector().resourceId(\"com.fintech.wallet.sample:id/tx_list\")"
  + ".childSelector(new UiSelector().descriptionContains(\"beneficiary-row\"))")
@iOSXCUITFindBy(iOSNsPredicate = "type == 'XCUIElementTypeButton' AND name BEGINSWITH 'beneficiary-'")
private WebElement firstBeneficiaryRow;
```

Prefer iOS predicate string / class chain over XPath — they are markedly faster on XCUITest.

### Tier 4 — XPath (LAST resort, attribute-anchored only)

Allowed only when Tiers 1-3 cannot identify the element. **Never** use absolute/positional
XPath (`/hierarchy/.../android.widget.Button[3]`) — it is the #1 source of flaky drift. Anchor
on stable attributes and keep it shallow.

```java
// Acceptable: attribute-anchored, relative.
@AndroidFindBy(xpath = "//*[@resource-id='com.fintech.wallet.sample:id/btn_login']")
@iOSXCUITFindBy(xpath = "//XCUIElementTypeButton[@name='login-submit-button']")
private WebElement loginButton;
```

If you write XPath, leave a `// TIER4-XPATH:` comment explaining why Tiers 1-3 were impossible —
this is the signal the **locator-healer** agent and reviewers look for to push a11y-id adoption.

### Tier 5 — AI visual fallback (appium-mcp), runtime-only, never committed as a locator

When the element genuinely cannot be addressed by the DOM/hierarchy (e.g. a canvas-drawn chart,
a `WEBVIEW` 3-D Secure control, an OTP keypad rendered as an image), the **locator-healer**
agent may use the **appium-mcp** AI-visual capability to locate by on-screen appearance and
propose a coordinate/region tap. Constraints:

- This is a **diagnostic / proposal** path, **not** a committed locator. The healer must
  convert the finding back into a Tier 1-4 selector (or file a request to add an accessibility
  id) before any change lands in a page object or `*.json`.
- AI-visual must **never** screenshot or reason over a screen showing an unmasked PAN/OTP/CVV.
  Treat those screens as sensitive: `ScreenshotUtil.capture(name, /*sensitive*/ true)` returns
  `null` by design (pixels can't be masked), and the visual fallback must be skipped there.
- Hybrid screens: switch context first via `BasePage.switchToWebView()` /
  `switchToNative()` — many "missing" elements are simply in the other context, which is far
  cheaper to fix than an AI-visual match.

## Retry / backoff rules

Self-healing is **bounded, explicit-wait-based retrying** — not infinite loops, not sleeps.

| Situation | Action | Bound |
|---|---|---|
| Element not yet rendered | rely on `waitForVisible`/`waitForClickable` (explicit wait) | `explicit.wait.seconds` (20s) |
| `StaleElementReferenceException` | re-resolve via the PageFactory proxy and retry the action once | 1 retry |
| Element off-screen | `scrollToElement(element)` (bounded swipes) | 8 swipe attempts (see `BasePage`) |
| Element in the other context | `switchToWebView()` / `switchToNative()` then retry | 1 switch |
| Transient `NoSuchElement` on a known-good locator | retry the whole step | up to 2 attempts, capped-exponential backoff |

A correct bounded retry helper looks like this (explicit waits, capped exponential backoff, no
`Thread.sleep`, mask in logs). It is the shape the locator-healer agent should generate:

```java
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;

/** Re-attempts a resolve-and-act on a PageFactory element, bounded and explicit-wait based. */
protected <T> T withHealing(java.util.function.Supplier<T> action) {
    final int maxAttempts = ConfigManager.getInt("locator.retry.max", 2) + 1;
    final long baseMs = ConfigManager.getInt("locator.retry.base.millis", 250);
    RuntimeException last = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        try {
            return action.get();
        } catch (StaleElementReferenceException | TimeoutException
                 | org.openqa.selenium.NoSuchElementException e) {
            last = e;
            log.warn("Locator attempt {}/{} failed: {}", attempt, maxAttempts,
                    MaskingUtil.mask(e.getMessage()));
            if (attempt == maxAttempts) break;
            // Capped exponential backoff WITHOUT Thread.sleep: poll a cheap always-true
            // condition so the wait is driver-pumped and interruptible.
            long capMs = Math.min(baseMs * (1L << (attempt - 1)), 2000);
            new org.openqa.selenium.support.ui.WebDriverWait(driver,
                    java.time.Duration.ofMillis(capMs))
                .ignoring(TimeoutException.class)
                .until(d -> false == true ? d : null); // bounded, no busy-spin, no sleep
        }
    }
    throw last;
}
```

Backoff config keys (add to `config.properties` with non-secret defaults):
`locator.retry.max=2`, `locator.retry.base.millis=250`. Never set `locator.retry.max` so high
that a genuinely-removed control wastes the whole `newCommandTimeout` budget.

## How `LocatorRepository` externalization powers healing

`com.fintech.qa.core.locators.LocatorRepository` is the **escape hatch** for locators that need
to change **without recompiling** — exactly the locators most likely to drift. Pages still use
annotations *primarily*; the repository is for the dynamic/overridable ones.

- Files live at `src/test/resources/locators/<page>-<platform>.json` — a flat
  `key -> selectorString` object. See `login-android.json` (resource-ids) and
  `login-ios.json` (accessibility ids). Keys starting with `_` (e.g. `_comment`) are metadata
  and skipped by the loader.
- Lookup: `LocatorRepository.get("login", "loginButton")` resolves the current platform via
  `Platform.current()`; `get(page, key, Platform)` and `get(page, key, default)` overloads
  exist. Results are cached per `page+platform`; `clearCache()` lets a test swap files.
- The repository returns a **raw selector string** — the caller turns it into a `By`
  (`AppiumBy.id(...)`, `AppiumBy.accessibilityId(...)`, `AppiumBy.xpath(...)`) and resolves it
  **inside `BasePage`** (the only place allowed to `findElement`). Do not resolve it in a page.

**Why this matters for self-healing:** when a screen ships a renamed id, the healer (or a human)
can hot-patch the value in the JSON and rerun **without a code change or rebuild** — the fastest
possible mitigation. Promote a brittle annotated locator into the repository when it has drifted
**twice**, or when it differs per environment/build flavor. Keep the annotation and the JSON in
sync (the `login-*.json` files intentionally mirror `LoginPage`).

## The locator-healer agent — division of labour

This skill is the **playbook**; the **locator-healer** agent is the **operator** that applies it
on a failing run. When a scenario fails on element resolution, the agent should:

1. **Classify the failure** (see escalation table below) from the stack trace and the page
   source — `TimeoutException` vs `NoSuchElement` vs `StaleElement` vs wrong-context.
2. **Try cheap fixes first**, in order: confirm context (`NATIVE_APP` vs `WEBVIEW`) →
   `scrollToElement` → re-resolve stale proxy → bounded retry with backoff.
3. **Re-rank the locator** up this skill's priority ladder. If the element has an accessibility
   id, switch to Tier 1; if it only had absolute XPath, replace with an attribute-anchored Tier
   2/3 selector.
4. **Apply the lowest-blast-radius change:** prefer editing the `<page>-<platform>.json` value;
   only touch the `@AndroidFindBy`/`@iOSXCUITFindBy` annotation when the locator is annotation-
   resident. Keep annotation + JSON mirrored.
5. **Verify** by re-running just the failing scenario (tag-filtered) and confirm `mvn -q
   -DskipTests compile` still passes; never weaken assertions to make it pass.
6. **Mask** every note/log it emits, and **never** add `driver.findElement` outside `BasePage`
   or any `Thread.sleep`.

The healer's AI-visual (appium-mcp) usage is strictly Tier 5: a **proposal** it must translate
back into a committed Tier 1-4 selector or an "add an accessibility id" request.

## When to auto-heal vs escalate to a human

Heal automatically only for **transient flake** or a **like-for-like locator rename**. Escalate
to a human for anything that implies a **behavioral or design change**.

| Signal | Diagnosis | Action |
|---|---|---|
| Passes on retry; intermittent `StaleElement`/`Timeout`; no app version bump | Transient flake | Auto-heal: bounded retry/backoff, maybe widen wait. No locator change. |
| Element present but **id/accessibility id renamed**, same role/position | Locator drift | Auto-heal: re-point JSON/annotation, prefer higher tier. |
| Element exists only in `WEBVIEW`/`NATIVE_APP` other context | Context bug | Auto-heal: `switchToWebView()`/`switchToNative()`. |
| Element **missing entirely** from page source across retries | Screen redesign / removed feature | **ESCALATE** — likely a real regression or flow change. |
| New/extra step, reordered screens, new permission/root/SSL-pin/biometric prompt | Flow change | **ESCALATE** — needs new steps/scenario, not a locator tweak. |
| Failure on a `@PCI-DSS-*` / `@REQ-*` security-negative scenario (session timeout, jailbreak prompt, SSL-pinning UX, app-backgrounding) | Compliance-sensitive | **ESCALATE** — never silently "heal" a security assertion. |
| Only an AI-visual (Tier 5) match can find it, no DOM handle | Missing a11y affordance | **ESCALATE** — request an accessibility id from the app team (also an a11y defect). |
| Heal would require `driver.findElement` in a page, a `Thread.sleep`, or weakening an assertion | Rule violation | **ESCALATE** — do not commit; the fix is wrong. |

Rule of thumb: **renames and flake heal; redesigns and new flows escalate.** If you can't prove
the element still exists and still means the same thing, stop and hand off to a human with the
masked stack trace, the relevant page-source excerpt, and the proposed (rejected) change.

## Checklist before committing a locator change

1. Locator sits at the **highest feasible tier**; any XPath carries a `// TIER4-XPATH:` reason.
2. Android resource-ids are fully qualified with `com.fintech.wallet.sample:id/...`.
3. Annotation and `src/test/resources/locators/<page>-<platform>.json` are **in sync**.
4. No `driver.findElement` outside `BasePage`; no `Thread.sleep`; explicit waits only.
5. Logs/notes are masked; no PAN/OTP/CVV/account/token leaked; no AI-visual over sensitive
   screens.
6. Re-run the failing scenario green and `mvn -q -DskipTests compile` passes.
