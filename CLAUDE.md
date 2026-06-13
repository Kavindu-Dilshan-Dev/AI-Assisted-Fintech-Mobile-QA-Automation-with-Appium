# CLAUDE.md

Operating instructions for Claude Code in this repo. Terse by design — for full
human-facing docs (prerequisites, secrets table, package map, reports), read
[README.md](README.md). For detailed conventions, the `.claude/skills` carry the
depth; this file is the always-on summary and routing layer. Do not duplicate the
README or the skills here.

## What this is

`com.fintech.qa` — a mobile QA automation framework for a **synthetic** fintech
wallet (Appium + Cucumber + JUnit Platform, Java 17, Maven, single module).
It will be **published as a reusable Maven JAR** consumed by other test repos.

## Build & test

```bash
mvn test                              # Android (default platform)
mvn test -Dplatform=ios               # iOS
mvn test -Dcucumber.filter.tags="@REQ-LOGIN and not @wip"
mvn -o clean test-compile             # fast compile check (no device needed)
```

- Surefire runs **only** `runners/CucumberTestRunner.java`; scenarios are
  discovered by the Cucumber JUnit-Platform engine. Glue/plugins are fixed in
  `src/test/resources/junit-platform.properties` — treat them as contractual.
- Config resolution order (later wins): `config.properties` → `-D` system props →
  env vars. Verify build changes with the clean compile above.

## Architecture — and where code must live

- **`src/main/java` = the engine** (driver, config, BasePage, pages, components,
  data builders, reporting, security utils). This is what ships in the JAR, so
  **framework code stays here** — never move engine code into `src/test` to
  "simplify."
- **`src/test/java` = specs** (runners, step definitions, hooks) + `src/test/resources`
  (features, locators, capabilities, testdata). Never packaged into the JAR.
- **Library dependency-scope hygiene** (because consumers inherit our compile deps):
  - `logback-classic` is **`test`** scope — never force a logging binding on consumers; only `slf4j-api` is compile.
  - `cucumber-java` is **`provided`** — needed on the main compile classpath for `ExtentReportManager` (references the grasshopper adapter), but kept non-transitive/unpackaged.
  - Do **not** add new `compile`-scope dependencies that would leak onto consumers without a reason. Pinned versions in `pom.xml` are a contract — don't bump casually.

## Cardinal rules — never violate

- **Only `BasePage` may call `driver.findElement`.** Pages/components/steps go
  through BasePage helpers. → skill `pom-conventions`
- **No real PII/PAN, ever.** Cards are Luhn-valid from fake test BINs (`400000…`).
  **Mask via `MaskingUtil`** (PAN/CVV/OTP/account/IBAN/token) before any log,
  report line, or screenshot-related text. → skills `test-data-masking-pii`,
  `test-data-factory-builder` (agent)
- **Secrets only from environment variables** — never in `config.properties`,
  never hardcoded. → README "Configuration & secrets"
- **No `Thread.sleep`** (use explicit waits) and **no `System.out`** (use slf4j).
- **Security scenarios must carry `@REQ-*` / `@PCI-DSS-*` tags.** → skill
  `fintech-security-testing-checklist`
- **Locators are externalized JSON** mirrored under `src/test/resources/locators/`,
  resolved by priority (a11y id → id → XPath → visual). → skill
  `self-healing-locator-strategy`
- Assert in steps with **AssertJ**; steps stay thin and delegate to pages.
- **Scenarios must be re-runnable.** CRUD/stateful tests use per-run **unique data**
  (`TestDataFactory.unique*` / `UniqueData`) — never hardcoded natural keys — and never
  depend on another scenario's side effects or run order. Strategy is *unique data, no
  delete*. → skill `test-idempotency-and-reruns`

## Who owns what — delegate to the right agent

| Task | Agent / skill |
|------|---------------|
| Project structure, Maven module, runner/driver/config/BasePage plumbing | `framework-architect` |
| Page Objects / Components from live screens | `page-object-builder` |
| Cucumber step definitions + hooks | `step-definition-implementer` |
| `.feature` Gherkin authoring | `gherkin-author` |
| Synthetic test-data models/builders/factory | `test-data-factory-builder` |
| Broken/flaky Appium locators | `locator-healer` |
| CI/CD (GitHub Actions, device farm, artifacts) | `cicd-engineer` |
| Pre-merge convention review | `code-reviewer` |
| Pre-merge security/compliance gate | `security-compliance-reviewer` |
| Test-run summary / masking-evidence digest | `extent-report-analyst` |
| Commit/split/squash/branch/push | `jj-commit-agent` |

**Orchestrating across agents** (which agents run, in what order, where the
review/security/commit gates sit): the main loop is the orchestrator — there is no
orchestrator agent. → skill `agent-orchestration-playbook`

## Version control

**Jujutsu (`jj`) over a colocated Git backend** is the intended workflow.
Never let a real secret, PAN, OTP, or device-farm token enter history. → skill
`jujutsu-workflow`
