---
name: gherkin-style-guide
description: Use when writing or reviewing Gherkin .feature files - Given/When/Then phrasing, @smoke/@REQ-*/@PCI-DSS-* tags, Scenario Outlines, and sourcing Examples from synthetic TestDataFactory (no real PAN).
---

# Gherkin Style Guide (fintech mobile wallet)

Authoritative conventions for `.feature` files under
`src/test/resources/features/` (`login.feature`, `transfer.feature`,
`security.feature`). Apply this whenever you author or review Gherkin in this
repo. Scenarios run through `io.cucumber:cucumber-java:7.20.1` on the JUnit 5
platform (`cucumber-junit-platform-engine:7.20.1`), glued to step definitions in
`com.fintech.qa.stepdefinitions` and hooks in `com.fintech.qa.hooks` (see
`src/test/resources/junit-platform.properties`).

The golden rule for this domain: **a feature file is a compliance artifact, not
just a test.** No real PII/PAN ever appears, secrets never appear, OTP/biometrics
are simulated, and every scenario is traceable to a requirement and (where
relevant) a PCI-DSS control via tags.

---

## 1. Declarative, business-readable phrasing

Write steps in the **third person, present tense, declarative voice**. Describe
*what* the user/system does, never *how* the automation does it. The "how"
(locators, taps, waits) lives in `pages`/`components`, reached only through
`BasePage` — never leak it into Gherkin.

| Keyword | Tense / voice | Purpose |
|---------|---------------|---------|
| `Given` | past/state ("is logged in", "is on the login screen") | precondition / context already true |
| `When`  | present action ("the user enters…", "the user confirms…") | the single behaviour under test |
| `Then`  | present assertion ("the dashboard is displayed") | observable, verifiable outcome |
| `And` / `But` | inherit the prior keyword's intent | continue a clause; never start a scenario |

### Good (matches `transfer.feature`)

```gherkin
@smoke @android @ios @REQ-PAY-101 @PCI-DSS-4.1
Scenario: Successful transfer to a synthetic beneficiary confirmed with OTP
  Given the user is logged in and on the dashboard
  When the user opens the transfer screen
  And the user selects the synthetic beneficiary
  And the user enters a transfer amount of "25.00"
  And the user confirms the transfer with OTP
  Then the transfer is successful
  And a confirmation message is shown
```

### Bad — UI mechanics leak into the Gherkin

```gherkin
Scenario: transfer
  When I tap the element with id "btn_transfer"        # UI mechanics, not behaviour
  And I wait 5 seconds                                  # imperative wait; we never sleep in Gherkin either
  And I type "4000001234567899" into the amount field  # real-looking PAN literal — forbidden
  Then I see the green checkmark png                    # asserts pixels, not behaviour
```

Phrasing rules:

- **One behaviour per `When`.** Multiple `When`/`And` steps that each fire a UI
  action are fine, but a scenario tests exactly one outcome. If you need two
  outcomes, write two scenarios.
- **No first person ("I"), no automation nouns** (element, id, xpath, locator,
  driver, tap, swipe, sleep). The repo already phrases steps as "the user …" /
  "the … is displayed". Stay consistent.
- **`Then` must be observable.** "the dashboard is displayed", "the masked
  account balance is shown", "biometric authentication is rejected" — each maps
  to an AssertJ assertion in a step def (`assertThat(...).as(...).isTrue()`).
- **`Background`** holds only shared `Given` preconditions (e.g.
  `Given the wallet app is launched on the login screen`). Never put a `When`
  or an assertion in a `Background`.
- **Reuse existing step text verbatim.** Each unique sentence binds to one
  `@Given/@When/@Then`-annotated method. Before inventing a phrase, grep the
  step defs (`com.fintech.qa.stepdefinitions.*`) for an existing match so you
  reuse the glue instead of creating an unbound step.

---

## 2. Tag taxonomy

Every scenario carries a layered tag set. Order them: **suite → platform →
requirement → compliance.**

| Category | Tags | Meaning / rule |
|----------|------|----------------|
| Suite / scope | `@smoke`, `@regression` | `@smoke` = fast critical-path gate; `@regression` = broader coverage. Exactly one of these per scenario. |
| Platform | `@android`, `@ios` | Where the scenario is expected to run. Include **both** unless the behaviour is genuinely platform-specific. `Platform.current()` selects the driver; tags filter execution. |
| Domain (feature-level) | `@login`, `@authentication`, `@transfer`, `@payments`, `@security`, `@non-functional` | Applied on the `Feature:` line to classify the whole file. |
| Requirement traceability | `@REQ-*` | e.g. `@REQ-AUTH-014`, `@REQ-PAY-101`, `@REQ-SEC-203`. **Mandatory** — every scenario maps to at least one requirement id. |
| Compliance | `@PCI-DSS-*` | e.g. `@PCI-DSS-8.2` (auth), `@PCI-DSS-4.1` (transport), `@PCI-DSS-6.5` (secure dev), `@PCI-DSS-8.1.8` (session timeout). Required on any scenario touching auth, card data, transport, or session controls. |

Feature-level tags inherit to every scenario in the file. Put domain tags on the
`Feature:`; put suite/platform/REQ/PCI tags on each `Scenario`/`Scenario Outline`.

```gherkin
@login @authentication
Feature: Wallet login
  ...

  @smoke @android @ios @REQ-AUTH-014 @PCI-DSS-8.2
  Scenario: Successful login with valid credentials and OTP
    ...
```

Selecting tags at runtime (no code change needed — these match the configured
glue/plugins in `junit-platform.properties`):

```bash
# Smoke only
mvn test -Dcucumber.filter.tags="@smoke"

# Android regression, excluding security non-functional scenarios
mvn test -Dcucumber.filter.tags="@regression and @android and not @non-functional"

# Everything covering a PCI control family for an audit pull
mvn test -Dcucumber.filter.tags="@PCI-DSS-8.2 or @PCI-DSS-8.3"
```

Tag hygiene:

- Never invent ad-hoc tags (`@todo`, `@wip`, `@bug123`) in committed features —
  they fragment the taxonomy. Use the categories above.
- A scenario missing a `@REQ-*` tag is a review-blocker: it is untraceable.
- Security negative scenarios (session timeout, root/jailbreak, SSL-pinning,
  backgrounding-during-transaction) live in `security.feature` under
  `@security @non-functional` and each carry a `@REQ-SEC-*` + a `@PCI-DSS-*` tag
  (see existing `@REQ-SEC-201`…`@REQ-SEC-205`).

---

## 3. Scenario Outline conventions

Use a `Scenario Outline` + `Examples` **only when the same behaviour repeats
across data variants** (e.g. several transfer amounts). If the steps differ per
row, write separate `Scenario`s instead.

```gherkin
@regression @android @ios @REQ-PAY-103 @PCI-DSS-4.1
Scenario Outline: Transfers of varying synthetic amounts succeed
  Given the user is logged in and on the dashboard
  When the user opens the transfer screen
  And the user selects the synthetic beneficiary
  And the user enters a transfer amount of "<amount>"
  And the user confirms the transfer with OTP
  Then the transfer is successful

  Examples:
    | amount |
    | 1.00   |
    | 99.99  |
    | 250.00 |
```

Rules:

- **Title is generic**, not row-specific. "Transfers of varying synthetic
  amounts succeed" — not "Transfer 250.00".
- **Placeholders quoted exactly as the step expects.** The bound step is
  `@When("the user enters a transfer amount of {string}")`, so the outline must
  read `"<amount>"` (quotes around the angle-bracketed token). A bare `<amount>`
  would not match the `{string}` cucumber expression.
- **One concept per column.** Header names are lowercase, descriptive
  (`amount`, `currency`), and match the placeholder names.
- **Keep tables small and intentional** (boundary / representative values:
  smallest, typical, large). They are examples, not exhaustive fuzzing.
- **Tag the outline once** at the `Scenario Outline` level; rows inherit it.
- Multiple `Examples` blocks are allowed and can be individually tagged to split
  data sets across suites:

```gherkin
  @smoke
  Examples: critical-path amounts
    | amount |
    | 25.00  |

  @regression
  Examples: boundary amounts
    | amount  |
    | 0.01    |
    | 9999.99 |
```

---

## 4. Examples-table data sourcing — the PII / PAN rule (critical)

**The Examples table and step parameters may contain ONLY: harmless display
literals (amounts, currency codes, UI labels) and synthetic identifiers. They
must NEVER contain anything that looks like real financial data.**

The real card numbers, account numbers, beneficiaries, and OTPs come from the
synthetic data layer at runtime — they are *produced in the step definitions*,
not typed into Gherkin:

- Cards / accounts / beneficiaries / transactions →
  `com.fintech.qa.core.data.TestDataFactory`
  (`validCard()`, `cardWithBin(String)`, `checkingAccount()`, `beneficiary()`,
  `transfer(from, to, amount)`). Cards are Luhn-valid and built from the
  **clearly-fake `400000` test BIN** via `LuhnGenerator` — never a real issuer
  BIN. Models expose **masked** display getters
  (`getMaskedAccountNumber()`, `maskCardNumber()`-style output).
- OTP → `com.fintech.qa.core.security.StaticOtpProvider` (local/dev) or
  `TestApiOtpProvider` (test backend), chosen via `OtpProviderFactory.create()`.
  Never a real SMS, never a literal OTP in Gherkin.
- Passwords/tokens → environment only (`TEST_USER_PASSWORD`, `OTP_API_TOKEN`),
  read in steps (see `TransferSteps.resolvePassword()`). Never in `.feature`,
  never in `config.properties`.
- Pre-seeded synthetic beneficiaries live ALREADY-MASKED in
  `src/test/resources/testdata/beneficiaries.json` (e.g.
  `"maskedAccountNumber": "****7421"`). Full numbers are intentionally absent.

So the Gherkin says **"the synthetic beneficiary"** (a noun the step resolves via
`TestDataFactory.beneficiary()`), and the Examples table carries only the
amount.

### Good — table holds only a harmless display value

```gherkin
Scenario Outline: Transfers of varying synthetic amounts succeed
  ...
  And the user enters a transfer amount of "<amount>"
  ...
  Examples:
    | amount |
    | 1.00   |
    | 99.99  |
```

The step def builds the real (synthetic, masked) data:

```java
@When("the user enters a transfer amount of {string}")
public void the_user_enters_a_transfer_amount_of(String amount) {
    Transaction txn = TestDataFactory.transfer(sourceAccount, beneficiary, amount);
    transferPage.enterAmount(amount);
    // Only masked numbers are ever reported.
    ExtentReportManager.logInfo("Entered transfer amount " + amount
            + " from " + txn.getFrom().getMaskedAccountNumber()
            + " to " + txn.getTo().getMaskedAccountNumber());
}
```

### Bad — real-looking PAN / PII / OTP literals in the table (NEVER do this)

```gherkin
Scenario Outline: transfer with card        # ❌ multiple violations
  When the user pays with card "<pan>" cvv "<cvv>" otp "<otp>"
  ...
  Examples:
    | pan              | cvv | otp    | beneficiary_account | password   |
    | 4929939187355598 | 123 | 884512 | 12345678901234      | Hunter2!   |
```

Why it is rejected:

- `4929939187355598` is a 16-digit PAN literal — a `MaskingUtil` target and a
  PCI red flag. Cards come from `TestDataFactory.validCard()` /
  `cardWithBin("400000")`, masked to `**** **** **** 1234` before any log.
- `cvv` / `otp` columns embed sensitive codes in plaintext. OTP comes from an
  `OtpProvider`; CVV is synthetic and never logged.
- `beneficiary_account` is a full account number — use `the synthetic
  beneficiary` and let `getMaskedAccountNumber()` surface only the last 4.
- `password` is a secret — secrets come from env vars only, never Gherkin.

### Decision rule for any value you want to put in a table

> If a value is a card number, CVV, full account number, IBAN, OTP, password,
> token, or anything `MaskingUtil.mask()` would redact — **it does not belong in
> the `.feature` file.** Put a noun in the Gherkin ("a valid synthetic card",
> "the synthetic beneficiary", "the OTP challenge") and resolve the real
> synthetic value through `TestDataFactory` / `OtpProvider` / env in the step.
> Only display-safe values (amounts, currency codes, counts, UI text, boolean
> intent like "matching"/"non-matching") may appear inline.

---

## 5. Idempotent / re-runnable scenarios

**Every scenario must be safe to run repeatedly — locally and in CI — with no
manual cleanup between runs.** A scenario that passes once and then fails on the
second run because it left state behind (or collided with a hardcoded name) is a
defect, not a test. The disposable test env has no delete/teardown hook, so
idempotency is achieved by **uniqueness**, not by cleaning up.

The pattern for CRUD / stateful behaviour:

- **Per-run UNIQUE natural key, never a literal.** A create scenario provisions a
  target with a per-run-unique natural key — phrase it as **"a beneficiary with a
  unique name"**, never a fixed literal like `"John Smith"` or a hardcoded account
  that would collide on the second run. The uniqueness is minted in the step via
  `TestDataFactory.uniqueBeneficiary()` / `uniqueCheckingAccount()`, not invented
  in Gherkin.
- **Self-contained scenarios.** A create/edit/delete scenario provisions its OWN
  unique target — in `Background` or a `Given` — and never depends on another
  scenario having run first, nor on execution order. Do not edit "the beneficiary
  the previous scenario added"; add your own, then edit it.
- **Carry the key, don't re-type it.** The generated key is produced in the create
  step and stashed via
  `com.fintech.qa.core.context.ScenarioContext.put("beneficiaryName", name)`; later
  steps read it with `ScenarioContext.getString("beneficiaryName")`. Gherkin refers
  to it **declaratively** — **"the beneficiary I just added"** — it is NOT re-typed
  as a literal in a later step or in an Examples row.
- **Tag with `@crud`** in addition to the mandatory `@REQ-*` (and any applicable
  `@PCI-DSS-*`). This lets a maintainer filter the stateful suite
  (`-Dcucumber.filter.tags="@crud"`).
- **Scenario Outline rows are independent.** Each `Examples` row must be
  self-cleaning by uniqueness and must not depend on another row's state or
  ordering — each row mints its own unique key the same way.
- Masking still applies: any synthetic account/IBAN derived from the unique key
  that reaches a log, report line, or screenshot text is masked via `MaskingUtil`
  (see skill `test-data-masking-pii`). The unique *name* is display-safe; a
  generated *account/IBAN* is not.

For the end-to-end rationale, key-collision avoidance, and the no-delete strategy,
see skill **`test-idempotency-and-reruns`** — do not restate it here.

```gherkin
@regression @crud @android @ios @REQ-PAY-110
Scenario: Add a synthetic beneficiary with a unique name
  Given the user is logged in and on the dashboard
  When the user adds a beneficiary with a unique name
  Then the beneficiary I just added appears in the beneficiary list
```

---

## 6. Review checklist (run before committing a feature)

- [ ] Each `Scenario`/`Scenario Outline` has exactly one of `@smoke` / `@regression`.
- [ ] Platform tags present (`@android` and/or `@ios`); both unless platform-specific.
- [ ] At least one `@REQ-*` tag; `@PCI-DSS-*` on any auth/card/transport/session scenario.
- [ ] Steps are declarative, third-person, present tense — no "I", no element/locator/tap/sleep nouns.
- [ ] `Background` holds only `Given` preconditions, no actions or assertions.
- [ ] Every `Then` is observable and maps to an existing/added AssertJ step.
- [ ] Outline placeholders quoted to match the step's `{string}` expression (`"<amount>"`).
- [ ] **No PAN / CVV / OTP / full account / IBAN / password / token literals** anywhere — those are produced by `TestDataFactory` / `OtpProvider` / env in the steps.
- [ ] New step phrasing has a matching glue method in `com.fintech.qa.stepdefinitions`; existing phrasing reused verbatim.
- [ ] Security negatives (session timeout, root/jailbreak, SSL-pinning, backgrounding) tagged `@security @non-functional @REQ-SEC-* @PCI-DSS-*`.
- [ ] CRUD/stateful scenarios are re-runnable: tagged `@crud`, provision their OWN per-run-unique key (no hardcoded colliding name, no cross-scenario/order dependency), and refer back to the generated key declaratively ("the beneficiary I just added") via `ScenarioContext` rather than re-typing a literal. → skill `test-idempotency-and-reruns`
