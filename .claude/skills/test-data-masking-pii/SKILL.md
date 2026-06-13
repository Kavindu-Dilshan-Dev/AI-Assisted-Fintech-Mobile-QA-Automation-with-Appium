---
name: test-data-masking-pii
description: Use when generating fintech test data (cards/accounts/IBANs) or logging, reporting, or screenshotting any value, to keep PII synthetic and redact PAN/CVV/OTP/account/token before output.
---

# Test Data, Masking & PII Safety

Authoritative rules for this fintech mobile QA framework (Maven, Java 17, base package
`com.fintech.qa`) on two intertwined concerns:

1. **Producing synthetic, clearly-fake test data** — Luhn-valid cards, masked accounts/IBANs.
2. **Redacting sensitive fields** before they reach ANY log line, Extent report line, screenshot
   text, or `toString()`.

These are non-functional compliance rules. Violating them is a PCI-DSS / privacy defect, not a
style nit. Treat every finding here as blocking.

## The one inviolable rule: NEVER real PII / PAN

- No real card numbers, real issuer BINs, real names, emails, phone numbers, account numbers,
  IBANs, or any real PII ever enters the repo, fixtures, logs, or reports.
- Cards are **Luhn-valid but clearly fake**, generated from a fake **test BIN** range
  (`400000…`, declared as `CardBuilder.DEFAULT_TEST_BIN = "400000"`). Never paste a real BIN
  (e.g. a real Visa/Mastercard issuer prefix) even "just to test the parser."
- Secrets (passwords, OTP API tokens, device-farm tokens) come **only from environment
  variables** via `ConfigManager` (e.g. `TEST_USER_PASSWORD`, `OTP_API_TOKEN`,
  `DEVICE_FARM_TOKEN`). `config.properties` holds non-secret defaults only.
- OTPs come from `OtpProvider` (test backend or static), never a real SMS gateway.

## Generating synthetic cards — `LuhnGenerator` + `CardBuilder` + `TestDataFactory`

Do **not** hand-write card numbers. Use the factory so every PAN is Luhn-valid and fake-BIN based.

`io.appium...`-free pure helpers live in `src/main/java/com/fintech/qa/core/data`:

- `LuhnGenerator.generate(String binPrefix, int length)` → Luhn-valid numeric string of exactly
  `length` digits (prefix + body + trailing check digit). Deterministic (seeded `java.util.Random`)
  so test data is reproducible.
- `LuhnGenerator.isValid(String number)` → validates the mod-10 checksum.
- `CardBuilder` defaults: `DEFAULT_TEST_BIN = "400000"`, `DEFAULT_PAN_LENGTH = 16`, fake holder
  `"Ada Testwell"`, `cvv = "123"`, `expiry 12/2030`.
- `TestDataFactory.validCard()`, `cardWithBin(String bin)`, `checkingAccount()`, `beneficiary()`,
  `transfer(from, to, amount)`.

```java
import com.fintech.qa.core.data.TestDataFactory;
import com.fintech.qa.core.data.LuhnGenerator;
import com.fintech.qa.core.data.model.Card;

// CORRECT: factory produces a Luhn-valid, fake-BIN (400000…) synthetic card
Card card = TestDataFactory.validCard();
assert LuhnGenerator.isValid(card.getPan());          // mod-10 valid
log.info("Using test card {}", card.getMaskedPan());  // "**** **** **** 1234"

// CORRECT: explicit fake BIN for a BIN-specific scenario
Card amexShaped = TestDataFactory.cardWithBin("371449"); // a FAKE test prefix, not a live issuer

// WRONG — never do any of these:
// new Card("4242424242424242", ...);  // a real, well-known live PAN pattern
// String pan = "5500005555555559";    // hand-typed, may collide with a real range
// log.info("pan={}", card.getPan());  // raw PAN to a log
```

Generate a non-default length/prefix only through the generator:

```java
String fakePan = LuhnGenerator.generate("400000", 16);   // 16-digit Luhn-valid synthetic PAN
boolean ok    = LuhnGenerator.isValid(fakePan);          // true
```

## Models expose masked display getters — use them in logs/reports

The model classes in `core.data.model` already redact in their display getters and `toString()`.
Prefer these over raw fields anywhere output is produced.

| Model      | Raw (drive app only)                 | Masked display (log/report)                          |
|------------|--------------------------------------|------------------------------------------------------|
| `Card`     | `getPan()`, `getCvv()`               | `getMaskedPan()` → `"**** **** **** 1234"`, `getMaskedCvv()` |
| `Account`  | `getAccountNumber()`                 | `getMaskedAccountNumber()` → `"********7890"`         |

`Card.toString()` / `Account.toString()` render ONLY the masked value, so a stray
`log.info("{}", card)` cannot leak a synthetic-but-realistic number. Still never log the raw
getter result directly.

## Redaction — `MaskingUtil` (`core.security.MaskingUtil`)

`MaskingUtil` is pure, null-safe, and has no I/O — safe to call from logging hot-paths. Every log
line, report message, and screenshot-related string MUST pass through it first.

```java
import com.fintech.qa.core.security.MaskingUtil;

String safe = MaskingUtil.mask(rawMessage);          // redacts all known sensitive patterns
String pan  = MaskingUtil.maskCardNumber(rawPan);    // "**** **** **** 1234"
String acct = MaskingUtil.maskAccountNumber(rawAcct);// keeps last 4: "********7890"
```

### What `mask(String)` redacts, and the exact patterns

Ordering matters and is intentional: structured/labelled tokens are masked **before** bare numeric
runs, so a long secret is never partially eaten by a more permissive rule (JWT → bearer → IBAN →
labelled CVV/OTP/account → PAN → standalone account).

| Field            | Behaviour                              | Pattern (as implemented)                                                                 |
|------------------|----------------------------------------|------------------------------------------------------------------------------------------|
| JWT              | whole token → `[REDACTED]`             | `\b[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b`                          |
| Bearer token     | value → `[REDACTED]`, keeps `Bearer`   | `(?i)(bearer)\s+[A-Za-z0-9._~+/=-]{8,}`                                                   |
| IBAN             | keep last 4, mask the rest             | `\b([A-Z]{2}\d{2})(?:[ ]?[A-Za-z0-9]){4,30}\b`                                            |
| CVV / CVC        | labelled value → `[REDACTED]`          | `(?i)\b(cvv|cvc|cvv2|cid|security\s*code)\b\s*[:=]?\s*\d{3,4}`                             |
| OTP / passcode   | labelled value → `[REDACTED]`          | `(?i)\b(otp|one[\s-]*time...|verification\s*code|passcode|auth\s*code)\b\s*[:=]?\s*\d{4,8}` |
| Account (labelled)| keep last 4                           | `(?i)\b(acc(?:ount|t)?(?:\s*(?:no|num(?:ber)?|#))?)\b\s*[:=]?\s*\d{6,18}`                 |
| PAN / card       | keep last 4 → `**** **** **** 1234`    | `(?<![\dA-Za-z])(?:\d[ -]?){13,19}(?![\dA-Za-z])`                                          |
| Account (bare)   | keep last 4                            | `(?<![\dA-Za-z])\d{7,12}(?![\dA-Za-z])`                                                    |

Examples of the contract in action:

```java
MaskingUtil.mask("PAN 4000001234567899 ok");      // "PAN **** **** **** 7899 ok"
MaskingUtil.mask("cvv: 123");                       // "cvv: [REDACTED]"
MaskingUtil.mask("OTP 123456 sent");                // "OTP: [REDACTED] sent"
MaskingUtil.mask("acct no 12345678");               // "acct: ****5678"
MaskingUtil.mask("Authorization: Bearer abcdef123456"); // "Authorization: Bearer [REDACTED]"
MaskingUtil.mask("DE89370400440532013000");         // IBAN → "****...3000" (last 4 kept)
MaskingUtil.maskCardNumber("4000 0012 3456 7899");  // "**** **** **** 7899"
MaskingUtil.maskAccountNumber("1234567890");        // "******7890"
MaskingUtil.mask(null);                             // null  (null-safe, returned unchanged)
```

### Editing `MaskingUtil` rules

If you add or widen a pattern: (a) keep the structured-before-numeric ordering in `mask()`;
(b) keep the class pure (no logging, no I/O — it is called from log paths and would recurse/deadlock
otherwise); (c) keep null-safety. Add a unit test under `src/test/java` asserting both that secrets
are redacted and that adjacent non-secret text is preserved.

## Where masking is already wired (don't double-handle, don't bypass)

These callers mask for you — call them and pass plain text; they sanitise internally:

- `BasePage.typeText(WebElement, String)` logs via `MaskingUtil.mask(text)` — so typing an OTP/PAN
  into a field is already safe. Located at `core.base.BasePage`.
- `BasePage.contentDescription(WebElement)` masks the returned a11y string before debug-logging.
- `ExtentReportManager.logInfo/logPass/logFail(String)` and `attachScreenshot(String)` each run the
  argument through `MaskingUtil.mask(...)` before it reaches the single grasshopper Extent engine.
  Do **not** create a second `ExtentReports` instance — the adapter configured by
  `src/test/resources/extent.properties` is the only engine.
- `ScreenshotUtil.capture(name, sensitive)` — when `sensitive == true` it **skips** the capture and
  returns `null` (image pixels cannot be masked). Pass that `null` straight to
  `ExtentReportManager.attachScreenshot(...)`, which no-ops on null/blank.

```java
// Sensitive screen (OTP entry / card form): never persist pixels
String shot = ScreenshotUtil.capture("otp-entry", /* sensitive */ true); // returns null
ExtentReportManager.attachScreenshot(shot);   // safe no-op
```

## Do / Don't (review checklist)

- DO build cards via `TestDataFactory` / `CardBuilder` / `LuhnGenerator` with the `400000…` fake BIN.
- DO log `card.getMaskedPan()`, `account.getMaskedAccountNumber()` — never the raw getters.
- DO wrap any free-form string in `MaskingUtil.mask(...)` before `log.*`/report unless it goes
  through `BasePage`/`ExtentReportManager`, which already mask.
- DO read secrets via `ConfigManager.get(...)` sourced from env vars only.
- DON'T hardcode a PAN, CVV, OTP, account number, IBAN, password, or token anywhere in source,
  `config.properties`, feature files, or fixtures.
- DON'T use `System.out` (use slf4j) and DON'T attach raw screenshots of card/OTP screens.
- DON'T add a real issuer BIN, even temporarily.
