---
name: jujutsu-workflow
description: Use when committing, splitting, squashing, branching, or pushing changes in this repo with Jujutsu (jj) over colocated Git. Covers the working-copy-as-commit model, bookmark conventions, the CI push flow, and never committing secrets to jj/git history.
---

# Jujutsu (jj) Workflow for the Fintech Mobile QA Framework

This repo is **Maven, Java 17, single module**, base package `com.fintech.qa`. We use
**Jujutsu (`jj`)** as the day-to-day VCS layered on top of a **colocated Git** repo, so
GitHub / CI tooling keeps working unchanged. This skill is the cheat sheet for working
with `jj` here.

> **jj is not installed locally yet.** Before any `jj` command works you must install it.
> Until then, fall back to plain `git` (the `.git` dir is the source of truth and is fully
> compatible). Install:
>
> ```bash
> # Windows (this dev box)
> winget install jj-vcs.jj          # or: scoop install jujutsu
> # macOS / Linux CI agents
> brew install jj                   # or: cargo install --locked jj-cli
> jj --version                       # verify before relying on it
> ```

---

## 0. The one rule that overrides everything: NEVER commit secrets

This is a **fintech** framework. The non-functional contract forbids real PII/PAN and any
hardcoded secret. `jj` makes *every* working-copy change a commit automatically, so it is
*easier* to leak a secret into history than with Git. Stay disciplined:

- **Secrets live ONLY in environment variables.** Never in tracked files. Examples used by
  this framework: `TEST_USER_PASSWORD`, `OTP_API_TOKEN`, `DEVICE_FARM_TOKEN`.
  `ConfigManager` load order is: `src/test/resources/config/config.properties` (non-secret
  defaults only) -> JVM system properties -> environment variables (`KEY` -> `UPPER_SNAKE_CASE`).
- **`config.properties` holds non-secret defaults only.** No tokens, no passwords, no real BINs.
- **Test data is synthetic.** Cards come from `LuhnGenerator` using clearly-fake test BINs
  (e.g. `400000...`); never a real issuer BIN or real account number. `MaskingUtil` masks
  PAN/CVV/OTP/account/IBAN/JWT before any log or report line — keep it that way in code you commit.
- **Never paste an OTP, fingerprint payload, JWT, or device-farm token into a commit message**
  (`jj describe`) or a feature file. Use `OtpProvider` / `BiometricHelper` indirection instead.

Pre-flight scan before describing/pushing — verify nothing sensitive is staged in the working-copy commit:

```bash
# Show what is in the current working-copy commit (the "@" change)
jj diff

# Grep the working-copy diff for likely secret/PII patterns BEFORE you push
jj diff --git | grep -nEi \
  '(password|passwd|secret|token|bearer|authorization|api[_-]?key|otp\.static=|[0-9]{13,19})' \
  && echo 'POTENTIAL SECRET/PAN — DO NOT PUSH' || echo 'clean'
```

If a secret already landed in history, see **§8 Removing a leaked secret**.

Belt-and-suspenders: keep these patterns ignored so they can never be tracked. `jj` honours
`.gitignore` in a colocated repo.

```gitignore
# .gitignore (already / should be present)
*.env
.env
.env.*
**/secrets.properties
**/*-local.properties
target/
target/screenshots/
target/cucumber.json
target/cucumber.xml
```

---

## 1. Mental model: the working copy IS a commit

In `jj` there is no staging area and no "dirty working tree". Your edits are continuously
recorded into the **working-copy commit**, addressed as `@`. Its parent is `@-`.

- Editing files = amending `@`. No `git add` needed.
- "Starting fresh work" = `jj new` (creates a new empty commit on top; the old one is now `@-`).
- A commit with no description shows as `(no description set)` and is fine *while you work* —
  give it one with `jj describe` before pushing.
- Conflicts are recorded *in* commits (first-class), so a rebase never leaves you in a broken
  detached state. You resolve conflicts as normal edits, then continue.

```text
@   (working copy — your live edits to BasePage/MaskingUtil/etc.)
|
o   feat: add MaskingUtil PAN redaction        <- @- (parent)
|
o   chore: scaffold core.driver package
|
~   main (bookmark tracking origin/main)
```

---

## 2. First-time setup: colocate jj with the existing Git repo

`env` reports this dir is **not** a git repo yet. Two cases:

```bash
cd "d:/Kavindu/Mobile Automation/Appium Sample 01"

# Case A — brand new repo (no .git here yet): create a colocated repo from scratch
jj git init --colocate

# Case B — a Git repo already exists / you just cloned one: colocate jj into it
jj git init --colocate        # run from inside the existing .git working tree
```

`--colocate` keeps a real `.git` directory beside `.jj`, so:

- IntelliJ, Maven, `gh`, and CI see a normal Git repo.
- Every `jj` command auto-imports/exports refs to Git — no manual sync needed.

Identity (matches the commit-author convention used elsewhere in this project):

```bash
jj config set --user user.name  "Your Name"
jj config set --user user.email "you@epiclanka.net"
```

---

## 3. Everyday loop

```bash
# See where you are: current change, parents, bookmarks, conflict markers
jj status
jj log            # graph of recent commits; @ is the working copy

# 1) Make code changes normally (edit BasePage.java, add a step def, etc.)
#    They are already "committed" into @ — nothing to stage.

# 2) Describe the work (this becomes the Git commit message)
jj describe -m "feat(security): mask OTP + CVV in BasePage.typeText logging"

# 3) Start the NEXT piece of work on top (closes off the described commit)
jj new
```

Commit-message style (mirrors the project's Git convention). Conventional-commits prefixes,
imperative mood, scope = package/area:

```text
feat(transfer): confirmWithOtp uses OtpProvider, never real SMS
fix(driver): DriverManager ThreadLocal leak on quitDriver
test(login): @REQ-1042 @PCI-DSS-3.4 biometric login negative path
chore(deps): pin appium java-client 9.3.0
```

Co-author trailer when pair-/AI-assisted (project standard):

```text
Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## 4. `describe` / `new` / `split` / `squash` — the core verbs

### `jj describe` — set/replace the message of a commit
```bash
jj describe -m "refactor(core.reporting): ExtentReportManager delegates to ExtentCucumberAdapter"
jj describe @-                 # edit the PARENT's message (opens editor)
jj describe -r <change-id>     # describe any commit by id
```

### `jj new` — begin a new change
```bash
jj new                         # new empty child of @ (most common)
jj new main                    # branch a fresh change off the main bookmark
jj new <id1> <id2>             # merge commit with two parents
```

### `jj split` — break one fat commit into focused ones
Use when `@` mixes unrelated work (e.g. you touched `CapabilitiesBuilder` *and* `LoginPage`
in one go). Keeps history reviewable for QA PRs.

```bash
jj split                       # interactive: pick hunks for the FIRST commit; rest stays in @
jj split src/main/java/com/fintech/qa/core/driver/CapabilitiesBuilder.java
#   ^ everything matching that path goes into the first commit; the remainder into the second
```

Concrete example — separate a driver-caps change from an unrelated page change:

```bash
# @ currently contains BOTH CapabilitiesBuilder.java and LoginPage.java edits
jj split src/main/java/com/fintech/qa/core/driver/CapabilitiesBuilder.java
jj describe @-  -m "feat(driver): biometric-friendly caps in CapabilitiesBuilder.androidOptions"
jj describe @   -m "feat(login): LoginPage.loginWithBiometric() wiring"
```

### `jj squash` — fold changes together
```bash
jj squash                      # move ALL of @ into its parent @- (then @ is empty again)
jj squash -i                   # interactively choose which hunks to fold down
jj squash -r <child> --into <parent>
```

Typical fixup flow ("I forgot to mask a log line in the commit I already described"):

```bash
# edit the file to add MaskingUtil.mask(...)
jj squash                      # folds the fix into @- ; message preserved; no rebase dance
```

### Reorder / move work around
```bash
jj rebase -r <change-id> -d main      # move a change onto main
jj edit <change-id>                    # make an older commit the working copy to amend it
jj abandon <change-id>                 # drop a commit entirely (e.g. an accidental secret commit)
jj undo                                # undo the LAST jj operation (op-log powered; very safe)
jj op log                              # full operation history if you need to recover further
```

---

## 5. Bookmark conventions (jj's "branches")

`jj` calls branches **bookmarks**. They do **not** move automatically with `@` — you point them
at a commit explicitly, then push. Naming convention for this repo:

| Bookmark pattern              | Use                                                        |
|-------------------------------|-----------------------------------------------------------|
| `main`                        | Mainline, tracks `origin/main`. Never push WIP here.      |
| `feat/<area>-<slug>`          | Feature work, e.g. `feat/transfer-otp-confirm`            |
| `fix/<area>-<slug>`           | Bug fix, e.g. `fix/driver-threadlocal-leak`               |
| `test/<req>-<slug>`           | Test/scenario work, e.g. `test/req-1042-biometric-login`  |
| `chore/<slug>`                | Tooling/deps/config, e.g. `chore/pin-appium-9.3.0`        |

`<area>` = the package you mostly touched: `driver`, `security`, `reporting`, `data`, `login`,
`transfer`, `dashboard`, `components`, `hooks`, `runners`.

```bash
jj bookmark create feat/transfer-otp-confirm -r @     # create at current commit
jj bookmark set    feat/transfer-otp-confirm -r @     # move it to current commit later
jj bookmark list                                       # show local + tracked remote bookmarks
jj bookmark track main@origin                          # track the remote main once
jj bookmark delete feat/transfer-otp-confirm           # remove after merge
```

Keep one bookmark per PR. Move it forward with `jj bookmark set ... -r @` as you add commits,
then push.

---

## 6. The colocated-git push workflow CI uses

CI consumes plain **Git** (`origin` on GitHub) and runs Maven. The `jj` -> Git bridge is
automatic in a colocated repo, but **you must point a bookmark and push it**.

```bash
# 1) Make sure the work is described and a bookmark points at it
jj describe -m "feat(transfer): @REQ-1042 OTP-confirmed transfer happy path"
jj bookmark set feat/transfer-otp-confirm -r @

# 2) Pull remote state first (import origin refs into jj)
jj git fetch

# 3) Rebase your bookmark onto the latest main so CI tests a current tree
jj rebase -b feat/transfer-otp-confirm -d main

# 4) Push the bookmark (creates/updates the matching Git branch on origin)
jj git push --bookmark feat/transfer-otp-confirm
#   first time, let jj name + create the remote branch:
jj git push -c @            # "-c" = create a bookmark for @ and push it
```

Then open the PR with the existing GitHub tooling:

```bash
gh pr create --fill --base main --head feat/transfer-otp-confirm
```

What CI does on that pushed branch (unchanged by jj):

```bash
mvn -B clean test
# JUnit Platform Suite runner: com.fintech.qa.runners.CucumberTestRunner
#   @Suite @IncludeEngines("cucumber") @SelectClasspathResource("features")
# Cucumber glue + Extent adapter wired via src/test/resources/junit-platform.properties:
#   cucumber.glue=com.fintech.qa.stepdefinitions,com.fintech.qa.hooks,
#                 com.aventstack.extentreports.cucumber.adapter
# Reports: target/cucumber.json, target/cucumber.xml, Extent (extent.properties),
#          screenshots under target/screenshots/
```

**Never** `jj git push` a bookmark that points at a commit containing a secret or unmasked
PAN/OTP/token — once it is on `origin`, treat it as compromised and rotate the secret.

### Sync after a merge
```bash
jj git fetch
jj rebase -d main                     # move your in-flight @ onto the new main
jj bookmark delete feat/transfer-otp-confirm   # tidy up the merged bookmark
```

---

## 7. Quick recipes for common QA-framework changes

```bash
# Add a new page object + its steps as TWO clean commits
# (edit pages/SettingsPage.java and stepdefinitions/SettingsSteps.java)
jj split src/main/java/com/fintech/qa/pages/SettingsPage.java
jj describe @- -m "feat(pages): SettingsPage extends BasePage"
jj describe @  -m "test(settings): SettingsSteps wiring"

# Amend a config default without touching code commits
# (edit src/test/resources/config/config.properties — non-secret defaults only!)
jj describe -m "chore(config): default explicit.wait.seconds=20"

# Bump a pinned dependency (keep versions EXACTLY as contracted)
# (edit pom.xml: io.appium:java-client:9.3.0, cucumber 7.20.1, etc.)
jj describe -m "chore(deps): align extentreports 5.1.2 + adapter 1.14.0"
```

---

## 8. Removing a leaked secret from jj/git history

If a token/PAN/password got committed (and especially if it reached `origin`):

```bash
# 1) Drop or fix the offending commit BEFORE pushing
jj edit <change-id>          # make it the working copy
# ...remove the secret from the file; route it through env/ConfigManager instead...
jj squash                    # fold the cleanup down, OR:
jj abandon <change-id>       # if the whole commit was a mistake

# 2) If it was ALREADY pushed:
#    - Rotate the credential immediately (TEST_USER_PASSWORD / OTP_API_TOKEN / DEVICE_FARM_TOKEN).
#    - Scrub history with git-filter-repo on the colocated .git, then force-update:
git filter-repo --replace-text <(printf 'THE_SECRET==>REDACTED\n')
jj git import                 # re-import the rewritten Git history into jj
jj git push --bookmark <name> --allow-new   # coordinate with the team; this rewrites remote
```

Prevention beats cleanup: run the **§0 pre-flight grep** before every `jj describe`/push, and
keep secrets in env vars only.

---

## 9. Cheat sheet

| Goal                                   | Command                                             |
|----------------------------------------|-----------------------------------------------------|
| Init colocated repo                    | `jj git init --colocate`                            |
| Where am I                             | `jj status` / `jj log`                              |
| See current change diff                | `jj diff` / `jj diff --git`                         |
| Name the current commit                | `jj describe -m "..."`                              |
| Start next change                      | `jj new`                                            |
| Split a fat commit                     | `jj split [path]`                                   |
| Fold change into parent               | `jj squash` / `jj squash -i`                         |
| Move a commit onto main                | `jj rebase -r <id> -d main`                          |
| Create / move bookmark                 | `jj bookmark create\|set <name> -r @`               |
| Fetch remote                           | `jj git fetch`                                       |
| Push a bookmark                        | `jj git push --bookmark <name>` (or `-c @`)         |
| Undo last jj op                        | `jj undo` (`jj op log` to go further)               |
| Drop a bad commit                      | `jj abandon <id>`                                   |

**Golden rules:** working copy is a commit (no staging); describe before you push; one bookmark
per PR; rebase onto `main` before pushing; and **never** let a secret, real PAN, OTP, or token
enter jj/git history — synthetic data + `MaskingUtil` + env-only secrets, always.
