---
name: agent-orchestration-playbook
description: Use when a task spans more than one specialist agent in this fintech QA framework — deciding WHICH agents run, in WHAT order, and where the review/security/commit gates go. Covers the canonical chains (new scenario, new screen, locator break, CI, data change, framework plumbing), the handoff contract between isolated subagents, and when to reach for the Workflow tool instead.
---

# Agent Orchestration Playbook

The fintech QA framework ships **eleven specialist agents** (see [CLAUDE.md](../../../CLAUDE.md)
"Who owns what") but **no orchestrator agent — by design.** This skill is the routing and
sequencing layer: it says which agents run, in what order, and where the guardrail gates sit.

## 1. The orchestrator is the main loop — not an agent

> **There is no orchestrator subagent, and you must not create one.** The top-level Claude Code
> session is the orchestrator. It reads the task, picks the chain below, and delegates each step
> to a specialist via the Agent tool.

Why a dedicated orchestrator *subagent* is an anti-pattern here:

- **Subagents can't reliably spawn subagents.** Every specialist (`framework-architect`,
  `page-object-builder`, `step-definition-implementer`, …) has a *scoped* toolset and **no Agent
  tool**. Only the main loop can fan out. An "orchestrator subagent" literally couldn't delegate.
- **Subagent context is isolated.** Each runs in its own context and returns only its final
  message. The main loop is the single place that holds the thread and **relays artifacts between
  steps** (a generated `.feature` path, the new page class name, a failing scenario id).
- The CLAUDE.md "Who owns what" table is the *static* routing; this playbook is the *dynamic*
  sequencing on top of it.

## 2. The handoff contract (how the main loop drives a chain)

Because subagents don't share memory, the orchestrating main loop owns these responsibilities:

1. **Branch the route.** Decide up front what the task actually needs — new data? new screen?
   security-tagged? — and pick the chain in §3. Don't run a step the task doesn't require.
2. **Relay artifacts, not vibes.** When you call the next agent, pass the concrete outputs of the
   previous one: file paths, class/method names, scenario tags, the exact failing step text.
3. **Run the gates before commit, every time** (§4). Generation agents *produce*; reviewer agents
   *gate*; the commit agent *publishes*. Never skip the gate to "save a round trip."
4. **Handle escalation.** `locator-healer` and the reviewers can *escalate instead of proceed*
   (e.g. a screen redesign, a BLOCK verdict). On escalation, re-route — don't force the next step.
5. **Respect the live-device dependency.** `page-object-builder` and `locator-healer` inspect a
   **running emulator/device via appium-mcp**. If none is up, that step blocks — surface it; have
   `cicd-engineer` or the user bring a device up first.

## 3. Canonical chains

Each step is `agent` → *(skill it follows)*. Gates are marked **[GATE]** and are read-only.

### A. New requirement / scenario → green test  *(the full end-to-end)*
```
1. gherkin-author              author .feature from the requirement   (gherkin-style-guide;
                               + @REQ-*/@PCI-DSS-* tags via fintech-security-testing-checklist)
2. test-data-factory-builder   ONLY IF new synthetic types/fields needed   (test-data-masking-pii)
3. page-object-builder         model NEW/CHANGED screens from live device  (pom-conventions,
                               self-healing-locator-strategy)        [needs running device]
4. step-definition-implementer wire thin steps + hooks; compile & run suite (pom-conventions)
5. code-reviewer          [GATE] POM/BasePage/flaky-smell/convention audit
6. security-compliance-reviewer [GATE] PII/secret/masking/tag final gate
7. extent-report-analyst       run digest + masking evidence (paste into PR)
8. jj-commit-agent             split/describe/push                    (jujutsu-workflow)
```
Skip 2 if the scenario reuses existing data; skip 3 if no screen changed (steps bind to existing
pages). Steps 5–6 are never skipped. Any **data-touching / CRUD** scenario (steps 1–4) must follow
`test-idempotency-and-reruns` — per-run unique data, self-contained, re-runnable.

### B. New / changed screen → Page Object only
```
1. page-object-builder         live-inspect, emit page + mirrored locator JSON
                               (pom-conventions, self-healing-locator-strategy)  [needs device]
2. step-definition-implementer ONLY IF a step references the new page methods
3. code-reviewer          [GATE]
4. jj-commit-agent
```

### C. Locator break / flaky element  *(NoSuchElement / Stale / Timeout)*
```
1. locator-healer              re-inspect running screen, climb a11y-id→id→XPath→visual
                               (self-healing-locator-strategy)        [needs device]
   └─ ESCALATES (does not silently heal) on: screen redesign, new flow, or any
      @REQ-*/@PCI-DSS-* security-negative failure → re-route to page-object-builder
      (redesign) or the user (security-negative).
2. step-definition-implementer ONLY IF a step's wording/contract changed
3. code-reviewer          [GATE]   → add security-compliance-reviewer [GATE] if @PCI-DSS-* touched
4. jj-commit-agent
```

### D. CI / device-farm pipeline
```
1. cicd-engineer               workflow YAML / Jenkinsfile / artifact publishing
                               (cicd-pipeline-templates, appium-capabilities-templates)
                               — invokes the build; does NOT author Java/pages/steps/Gherkin
2. extent-report-analyst       confirm Extent/cucumber/junit artifacts published AND masked
3. jj-commit-agent
```

### E. Synthetic test-data change
```
1. test-data-factory-builder   model/builder/factory/Luhn change          (test-data-masking-pii)
2. code-reviewer          [GATE]
3. jj-commit-agent
```

### F. Framework plumbing / structure
```
1. framework-architect         module/runner/DriverFactory/BasePage/Config spine
2. code-reviewer          [GATE]
3. jj-commit-agent
```

### G. Post-run triage  *(after ANY mvn test or CI run, no commit)*
```
1. extent-report-analyst       pass/fail/flaky digest + confirm no unmasked PAN/CVV/OTP/account/token
```

## 4. The guardrail gates (the part you never skip)

Run **before any code leaves a branch**, in this order:

```
   generation agent(s)
        │
        ▼
   code-reviewer            [GATE 1]  conventions, POM, flaky smells   (read-only)
        │
        ▼
   security-compliance-reviewer [GATE 2]  PII/secret/masking/tags — PASS|BLOCK  (read-only, FINAL)
        │   BLOCK ──► back to the owning generation agent to fix, then re-gate
        ▼
   jj-commit-agent          runs its OWN pre-flight secret/PII scan, then describe/push
```

- Both reviewers are **read-only** — they report `file:line` findings and a verdict; they never
  edit. The *owning* generation agent applies fixes, then you re-run the gate.
- `security-compliance-reviewer` is the **final** gate; `jj-commit-agent` is belt-and-suspenders
  (its own scan), not a substitute for it.
- Gate 2 is mandatory whenever the change touches auth, card data, OTP, biometrics, sessions,
  transport, logging, reporting, test data, or `src/test/resources` fixtures/features.

## 5. When to reach for the Workflow tool instead of hand-driving a chain

This playbook is for **interactive, judgement-heavy** authoring (most QA work). Switch to the
built-in **Workflow** orchestration only when the work is **deterministic, repeatable, and worth
parallelizing** — and only with explicit opt-in (the user says "use a workflow" / "ultracode"):

| Hand-drive the chain (this skill)                 | Use the Workflow tool                                  |
|---------------------------------------------------|--------------------------------------------------------|
| One new scenario / screen / fix, decisions needed | Fan-out review of *many* files/scenarios at once       |
| Step output shapes the next step                  | Fixed stages, same every run (find → verify → synth)   |
| Live-device authoring loop                         | Batch audits, migrations, broad sweeps                 |

The **CI execution** pipeline (build → emulator → Cucumber → publish → notify) is already
deterministic and **owned by `cicd-engineer` in YAML** — that's the right home for it, not an
ad-hoc Workflow.

## 6. Quick routing reference

| Trigger                                             | Chain | First agent                  |
|-----------------------------------------------------|-------|------------------------------|
| New requirement / user story / "scenario for X"     | A     | gherkin-author               |
| New or changed screen                               | B     | page-object-builder          |
| NoSuchElement / Stale / locator Timeout / drift     | C     | locator-healer               |
| "add CI" / device farm / publish reports            | D     | cicd-engineer                |
| New/changed card/account/beneficiary/transaction    | E     | test-data-factory-builder    |
| Module/runner/driver/config/BasePage plumbing       | F     | framework-architect          |
| "what failed" / test summary / masking evidence     | G     | extent-report-analyst        |
| Any pre-merge sign-off                              | §4    | code-reviewer → security-…   |
| Commit / split / squash / branch / push             | tail  | jj-commit-agent              |

**Golden rule:** the main loop orchestrates; specialists execute; reviewers gate; the commit agent
publishes. Generation → **code-reviewer → security-compliance-reviewer** → jj-commit-agent is the
spine every chain ends on.
