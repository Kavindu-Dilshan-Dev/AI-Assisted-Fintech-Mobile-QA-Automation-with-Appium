---
name: test-idempotency-and-reruns
description: Use when writing or reviewing CRUD / stateful scenarios and test data that must survive repeated CI re-runs in the fintech wallet QA framework — covers the "unique data, no delete" strategy, per-run UniqueData / TestDataFactory.unique*, carrying a generated key across steps via ScenarioContext, the CucumberHooks reset, TEST_RUN_ID in CI, and keeping scenarios self-contained (no ordering dependence). Pairs with gherkin-style-guide and test-data-masking-pii.
---

# Test Idempotency & Re-runnability

A scenario is **idempotent** here if it produces the same PASS result every time it runs — first
run, second run, in parallel, and in CI — **without any manual cleanup between runs**. Read-only
flows (login, transfer assertions, security negatives) are already idempotent. **CRUD / stateful
scenarios are not, unless you build them this way.**

## 1. The problem this prevents

A naive `Add user → Edit user → Delete user` suite passes once, then fails forever:

- **Run #2 of "Add"** hits *"user already exists"* — the natural key was a hardcoded literal that
  the first run already created.
- **Run #2 of "Delete"** hits *"not found"* — the entity was already deleted.
- Worse: if "Edit"/"Delete" depend on "Add" having run first (a shared, mutated fixture), they
  break whenever run alone, re-ordered, sharded, or retried.

The defaults make this easy to hit: `TestDataFactory` / the builders use **fixed natural keys**
(`"Ada Testwell"`, account `9000000000007421`, IBAN `GB00TEST…7421`). Those literals are exactly
what a "create" collides on. *(Card PANs are intentionally deterministic-from-fake-BIN — that is
correct and stays. A PAN is not a CRUD collision key; usernames / beneficiary names / references /
account numbers are.)*

## 2. The strategy: **unique data, no delete**

This framework is UI-only (Appium); there is no backend cleanup API. So we do **not** delete —
we make collisions impossible instead:

1. **Create never collides.** The create step mints a **per-run-unique** natural key
   (`<base>_<runId>_<seq>`) instead of a literal, so every run creates a *different* entity.
2. **Every scenario is self-contained.** A create / edit / delete scenario **provisions its own
   unique target first** (in `Background` or a `Given`), then operates on it. No scenario depends
   on a sibling having run, or on execution order.
3. **Carry the generated key across steps** via `ScenarioContext` — the create step stashes it,
   later steps read it. The Gherkin refers to it *declaratively* ("the beneficiary I just added"),
   never re-typing a literal.
4. **No teardown.** Synthetic data accumulates harmlessly in a **disposable / reset** test
   environment (or is swept externally — see §6). The only lifecycle hook is clearing
   `ScenarioContext` between scenarios. There is intentionally **no delete-after-test hook** — it
   would be slow and flaky, and uniqueness already removes the need.

## 3. How to apply it, layer by layer

### 3.1 Gherkin — self-contained, declarative, `@crud`-tagged
(See `gherkin-style-guide` §"Idempotent / re-runnable scenarios" for the full rule.)

```gherkin
@beneficiaries @payments
Feature: Synthetic beneficiary management

  Background:
    Given the user is logged in and on the dashboard

  @smoke @crud @android @ios @REQ-PAY-110
  Scenario: Add a beneficiary with a unique name
    When the user adds a beneficiary with a unique name
    Then the beneficiary I just added appears in the beneficiary list

  @regression @crud @android @ios @REQ-PAY-111
  Scenario: Edit a beneficiary I just added
    Given the user has added a beneficiary with a unique name
    When the user edits the beneficiary I just added to a new unique name
    Then the beneficiary I just added appears in the beneficiary list

  @regression @crud @android @ios @REQ-PAY-112
  Scenario: Delete a beneficiary I just added
    Given the user has added a beneficiary with a unique name
    When the user deletes the beneficiary I just added
    Then the beneficiary I just added is no longer in the beneficiary list
```

Note how **Edit** and **Delete** each carry their own `Given … has added …` precondition — they
never assume the **Add** scenario ran. Each Scenario Outline `Examples` row must likewise be
independent (uniqueness makes every row self-cleaning).

### 3.2 Mint unique data — `UniqueData` + `TestDataFactory.unique*`
(Owned by the `test-data-factory-builder` agent; follow `test-data-masking-pii`.)

```java
// Per-run-unique synthetic entities — natural keys never collide across runs:
Beneficiary b = TestDataFactory.uniqueBeneficiary();           // name "Ada_Testwell_<runId>_<seq>"
Beneficiary b2 = TestDataFactory.uniqueBeneficiary("Payee");   // caller-supplied base name
Account a = TestDataFactory.uniqueCheckingAccount();           // unique synthetic account number

// Low-level token helpers when you need your own unique key:
String runId = UniqueData.runId();        // stable for the whole JVM run (see §3.4 for CI)
String token = UniqueData.token();        // runId + "_" + monotonic counter (unique per call)
String name  = UniqueData.name("Payee");  // "Payee_<runId>_<seq>", sanitized to [A-Za-z0-9._-]
```

Use the `unique*` factory methods for CRUD. Keep using the plain deterministic methods
(`beneficiary()`, `validCard()`, …) for **read-only** flows where reproducibility is preferred and
nothing is persisted. All values stay obviously synthetic (`9…`/`1…` ranges, `GB00TEST…`).

### 3.3 Carry the key across steps — `ScenarioContext`
`ScenarioContext` (`com.fintech.qa.core.context`) is a ThreadLocal-backed static store, parallel-safe
like `DriverManager`. The create step stashes the generated key; edit/delete/assert steps read it.

```java
public class BeneficiarySteps {
    private static final String KEY = "beneficiaryName";
    private final BeneficiaryPage beneficiaryPage = new BeneficiaryPage();

    @When("the user adds a beneficiary with a unique name")
    public void adds_unique_beneficiary() { addUniqueBeneficiary(); }

    // Same provisioning reused as a Given so edit/delete are self-contained:
    @Given("the user has added a beneficiary with a unique name")
    public void has_added_unique_beneficiary() { addUniqueBeneficiary(); }

    @When("the user edits the beneficiary I just added to a new unique name")
    public void edits_to_new_unique_name() {
        String current = ScenarioContext.getString(KEY);
        Beneficiary renamed = TestDataFactory.uniqueBeneficiary();
        beneficiaryPage.rename(current, renamed.getName());
        ScenarioContext.put(KEY, renamed.getName());          // keep the stashed key current
    }

    @When("the user deletes the beneficiary I just added")
    public void deletes_it() { beneficiaryPage.delete(ScenarioContext.getString(KEY)); }

    @Then("the beneficiary I just added appears in the beneficiary list")
    public void appears() {
        String name = ScenarioContext.getString(KEY);
        assertThat(beneficiaryPage.isListed(name)).as("beneficiary %s listed", name).isTrue();
    }

    @Then("the beneficiary I just added is no longer in the beneficiary list")
    public void gone() {
        String name = ScenarioContext.getString(KEY);
        assertThat(beneficiaryPage.isListed(name)).as("beneficiary %s removed", name).isFalse();
    }

    private void addUniqueBeneficiary() {
        Beneficiary b = TestDataFactory.uniqueBeneficiary();
        beneficiaryPage.add(b);
        ScenarioContext.put(KEY, b.getName());
        // name is display-safe; the account is masked before it reaches the report:
        ExtentReportManager.logInfo("Added beneficiary " + b.getName()
                + " (" + MaskingUtil.mask(b.getAccountNumber()) + ")");
    }
}
```

Steps stay thin (delegate to page objects, AssertJ assertions, no raw driver, no business logic) —
see `pom-conventions`. The step phrasings above are illustrative; binding them needs a real
`BeneficiaryPage` (a follow-on, since no "manage beneficiary" screen exists yet).

### 3.4 Lifecycle — context is reset for you; nothing is deleted
`CucumberHooks` already clears `ScenarioContext` at scenario start *and* in the `@After` `finally`
block (prevents thread-local leakage on pooled workers). **You do not add cleanup.** There is no
delete hook by design.

### 3.5 Masking still applies
Uniqueness does not exempt a value from redaction. Any synthetic account number / IBAN / holder
name that reaches a log, report, or screenshot text must go through `MaskingUtil` (or the model's
masked display getter — `getMaskedAccountNumber()` / `getMaskedIban()`). See `test-data-masking-pii`.

## 4. Re-runnable vs. non-idempotent — the diff that matters

```gherkin
# ❌ NON-IDEMPOTENT — fails on the 2nd run; depends on scenario order
Scenario: Add beneficiary
  When the user adds beneficiary "Ada Testwell"          # literal key → collides on re-run
  Then "Ada Testwell" appears in the list
Scenario: Delete beneficiary
  When the user deletes beneficiary "Ada Testwell"       # assumes the scenario above ran first
  Then "Ada Testwell" is not in the list

# ✅ IDEMPOTENT — green every run, in any order, in parallel
Scenario: Delete a beneficiary I just added
  Given the user has added a beneficiary with a unique name   # provisions its own unique target
  When the user deletes the beneficiary I just added          # operates on what THIS run created
  Then the beneficiary I just added is no longer in the list
```

## 5. CI guidance

- Each JVM run self-mints a fresh `runId` when `TEST_RUN_ID` is unset, so re-runs never collide
  out of the box. **Set `TEST_RUN_ID` to the build/run number** in CI for traceable, collision-free
  keys across concurrent pipelines (`ConfigManager` reads it via `test.run.id`).
- Run the `@crud` scenarios **twice back-to-back** as the re-runnability gate:
  `mvn test -Dcucumber.filter.tags="@crud"` — both runs must be green.
- Target the disposable / reset environment for `@crud`; data is expected to accumulate there.

## 6. Review checklist (CRUD / stateful scenarios)

- [ ] Tagged `@crud` (plus the mandatory `@REQ-*` and any applicable `@PCI-DSS-*`).
- [ ] No hardcoded natural key — names/accounts/refs come from `TestDataFactory.unique*` /
      `UniqueData`, never a literal that could collide on re-run.
- [ ] Self-contained — each edit/delete scenario provisions its own target via `Background`/`Given`;
      no dependence on another scenario or on execution order.
- [ ] Generated key flows through `ScenarioContext`; Gherkin refers to it declaratively, not by
      re-typing a literal.
- [ ] No delete/teardown hook added (unique-data strategy); the only lifecycle change is the
      `ScenarioContext` reset already in `CucumberHooks`.
- [ ] Any synthetic account/IBAN/name in a log/report/screenshot is masked (`MaskingUtil` /
      masked getters).
- [ ] Card PANs left deterministic (unchanged); uniqueness applied only to collision-prone keys.
- [ ] Re-ran the `@crud` tag twice locally/CI — both green.

## 7. Where this fits

Cross-references: `gherkin-style-guide` (scenario phrasing/tags), `test-data-masking-pii` (synthetic
data + redaction), `pom-conventions` (thin steps, no raw driver). In `agent-orchestration-playbook`
this is the convention every **data-touching** scenario in Chain A (new requirement → green test)
must follow; ownership is shared across `gherkin-author`, `test-data-factory-builder`, and
`step-definition-implementer` — there is **no dedicated idempotency agent**.
