package com.fintech.qa.core.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Centralised, null-safe redaction utility for sensitive fintech data.
 *
 * <p>Every log line, report message, and screenshot-related text in the framework MUST be passed
 * through {@link #mask(String)} before it leaves the process. This class is pure (no side effects,
 * no logging, no I/O) so it can be safely invoked from any layer, including logging hot-paths.</p>
 *
 * <p>What gets redacted by {@link #mask(String)}:</p>
 * <ul>
 *   <li>PAN / card numbers: 13-19 consecutive digits (optionally separated by spaces or dashes),
 *       keeping only the last 4 digits.</li>
 *   <li>IBAN: 2 letters + 2 check digits + up to 30 alphanumerics, keeping the last 4.</li>
 *   <li>Account numbers: standalone 7-12 digit runs, keeping the last 4.</li>
 *   <li>CVV / CVC: 3-4 digit security codes when labelled.</li>
 *   <li>OTP / one-time passcodes: 4-8 digit codes when labelled.</li>
 *   <li>Bearer tokens and JWTs.</li>
 * </ul>
 *
 * <p>The class is final and cannot be instantiated.</p>
 */
public final class MaskingUtil {

    /** Replacement token used for fully redacted values. */
    private static final String REDACTED = "[REDACTED]";

    /**
     * JWT: three base64url segments separated by dots (header.payload.signature).
     * Matched before bearer/digit rules so token bodies are not partially eaten by the PAN rule.
     */
    private static final Pattern JWT =
            Pattern.compile("\\b[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b");

    /**
     * Bearer / authorization token: the literal "Bearer" (any case) followed by a token value.
     */
    private static final Pattern BEARER =
            Pattern.compile("(?i)(bearer)\\s+[A-Za-z0-9._~+/=-]{8,}");

    /**
     * IBAN: country code (2 letters) + 2 check digits + 4..30 alphanumerics, tolerating spaces.
     * Anchored on a word boundary so it does not swallow surrounding identifiers.
     */
    private static final Pattern IBAN =
            Pattern.compile("\\b([A-Z]{2}\\d{2})(?:[ ]?[A-Za-z0-9]){4,30}\\b");

    /**
     * PAN / card number: 13-19 digits, optionally grouped by single spaces or dashes.
     * Uses look-around so a longer digit run is not matched as a card by accident.
     */
    private static final Pattern PAN =
            Pattern.compile("(?<![\\dA-Za-z])(?:\\d[ -]?){13,19}(?![\\dA-Za-z])");

    /**
     * Labelled CVV / CVC / security code followed by a 3-4 digit value.
     */
    private static final Pattern CVV =
            Pattern.compile("(?i)\\b(cvv|cvc|cvv2|cid|security\\s*code)\\b\\s*[:=]?\\s*\\d{3,4}");

    /**
     * Labelled OTP / one-time passcode / verification code followed by a 4-8 digit value.
     */
    private static final Pattern OTP =
            Pattern.compile("(?i)\\b(otp|one[\\s-]*time[\\s-]*(?:pass(?:code|word)?)?|"
                    + "verification\\s*code|passcode|auth\\s*code)\\b\\s*[:=]?\\s*\\d{4,8}");

    /**
     * Labelled account number followed by a 6-18 digit value (label-driven so it is precise).
     */
    private static final Pattern LABELLED_ACCOUNT =
            Pattern.compile("(?i)\\b(acc(?:ount|t)?(?:\\s*(?:no|num(?:ber)?|#))?)\\b\\s*[:=]?\\s*\\d{6,18}");

    /**
     * Standalone numeric account run: 7-12 digits not adjacent to other digits/letters.
     * Applied after PAN so 13-19 digit cards are handled first.
     */
    private static final Pattern STANDALONE_ACCOUNT =
            Pattern.compile("(?<![\\dA-Za-z])\\d{7,12}(?![\\dA-Za-z])");

    private MaskingUtil() {
        throw new AssertionError("MaskingUtil is a utility class and must not be instantiated");
    }

    /**
     * Redacts all known sensitive patterns from the given text.
     *
     * <p>Ordering matters: structured/labelled tokens (JWT, bearer, IBAN, labelled CVV/OTP/account)
     * are masked first, then bare PANs, then bare account-number runs. This prevents a long secret
     * from being partially consumed by a more permissive numeric rule.</p>
     *
     * @param input the text to redact; may be {@code null}
     * @return the redacted text, or the original {@code null}/blank value unchanged
     */
    public static String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String result = input;

        result = replaceAll(result, JWT, m -> REDACTED);
        result = replaceAll(result, BEARER, m -> m.group(1) + " " + REDACTED);
        result = replaceAll(result, IBAN, m -> maskKeepLast4(stripSeparators(m.group())));
        result = replaceAll(result, CVV, m -> m.group(1) + ": " + REDACTED);
        result = replaceAll(result, OTP, m -> m.group(1) + ": " + REDACTED);
        result = replaceAll(result, LABELLED_ACCOUNT,
                m -> m.group(1) + ": " + maskAccountNumber(extractDigits(m.group())));
        result = replaceAll(result, PAN, m -> maskCardNumber(m.group()));
        result = replaceAll(result, STANDALONE_ACCOUNT, m -> maskAccountNumber(m.group()));

        return result;
    }

    /**
     * Formats a card number into the canonical masked display form {@code "**** **** **** 1234"}.
     *
     * @param cardNumber the raw card number (may contain spaces/dashes); may be {@code null}
     * @return the masked display string, or the original value when {@code null}/blank or having
     *         fewer than 4 digits
     */
    public static String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            return cardNumber;
        }
        String digits = stripSeparators(cardNumber);
        if (digits.length() < 4 || !digits.chars().allMatch(Character::isDigit)) {
            return cardNumber;
        }
        String last4 = digits.substring(digits.length() - 4);
        return "**** **** **** " + last4;
    }

    /**
     * Masks an account number keeping only its last 4 digits, e.g. {@code "********7890"}.
     *
     * @param accountNumber the raw account number; may be {@code null}
     * @return the masked account number, or the original value when {@code null}/blank or having
     *         fewer than 5 characters
     */
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return accountNumber;
        }
        String trimmed = accountNumber.trim();
        if (trimmed.length() <= 4) {
            return trimmed;
        }
        String last4 = trimmed.substring(trimmed.length() - 4);
        return "*".repeat(trimmed.length() - 4) + last4;
    }

    // ---------------------------------------------------------------------
    // internals
    // ---------------------------------------------------------------------

    /**
     * Functional replacement helper that applies {@code replacer} to each {@link Matcher} hit and
     * quotes the result so {@code $} and {@code \} in masked values are treated literally.
     */
    private static String replaceAll(String input, Pattern pattern,
                                     java.util.function.Function<Matcher, String> replacer) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacer.apply(matcher)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** Removes spaces and dashes used as grouping separators. */
    private static String stripSeparators(String value) {
        return value.replaceAll("[ -]", "");
    }

    /** Extracts only the digit characters from a value. */
    private static String extractDigits(String value) {
        return value.replaceAll("\\D", "");
    }

    /**
     * Generic "keep last 4" masker used for alphanumeric values such as IBANs: every leading
     * character is replaced with {@code *} and the last 4 kept verbatim.
     */
    private static String maskKeepLast4(String value) {
        if (value == null || value.length() <= 4) {
            return value;
        }
        String last4 = value.substring(value.length() - 4);
        return "*".repeat(value.length() - 4) + last4;
    }
}
