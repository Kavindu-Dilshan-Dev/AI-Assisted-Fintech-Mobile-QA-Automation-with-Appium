package com.fintech.qa.core.data;

import com.fintech.qa.core.data.builder.AccountBuilder;
import com.fintech.qa.core.data.builder.BeneficiaryBuilder;
import com.fintech.qa.core.data.builder.CardBuilder;
import com.fintech.qa.core.data.builder.TransactionBuilder;
import com.fintech.qa.core.data.model.Account;
import com.fintech.qa.core.data.model.Beneficiary;
import com.fintech.qa.core.data.model.Card;
import com.fintech.qa.core.data.model.Transaction;
import org.apache.commons.lang3.StringUtils;

/**
 * Central factory for <strong>synthetic, clearly fake</strong> fintech test data.
 *
 * <p>All cards are Luhn-valid and built from fake test BINs (e.g. the {@code 400000}
 * test range) via {@link LuhnGenerator}; no real PANs, issuer BINs, or PII ever appear.
 * Models expose masked display getters for safe logging/reporting.</p>
 */
public final class TestDataFactory {

    /** Clearly fake test BIN (Visa test range), NOT a real issuer BIN. */
    public static final String DEFAULT_TEST_BIN = CardBuilder.DEFAULT_TEST_BIN;

    private TestDataFactory() {
        // static factory
    }

    /**
     * Produces a valid synthetic card using the default fake test BIN.
     *
     * @return a Luhn-valid {@link Card} with synthetic details
     */
    public static Card validCard() {
        return new CardBuilder()
                .bin(DEFAULT_TEST_BIN)
                .holderName("Ada Testwell")
                .build();
    }

    /**
     * Produces a synthetic card whose PAN starts with the supplied (fake) BIN.
     *
     * @param bin the fake test BIN prefix; must be non-blank and numeric
     * @return a Luhn-valid {@link Card} starting with {@code bin}
     * @throws IllegalArgumentException if {@code bin} is blank or non-numeric
     */
    public static Card cardWithBin(String bin) {
        if (StringUtils.isBlank(bin)) {
            throw new IllegalArgumentException("bin must not be blank");
        }
        return new CardBuilder()
                .bin(bin)
                .holderName("Ada Testwell")
                .build();
    }

    /**
     * Produces a synthetic checking account.
     *
     * @return a {@link Account} of type {@code CHECKING}
     */
    public static Account checkingAccount() {
        return new AccountBuilder()
                .accountType("CHECKING")
                .currency("USD")
                .balance("1000.00")
                .holderName("Ada Testwell")
                .build();
    }

    /**
     * Produces a synthetic transfer beneficiary.
     *
     * @return a {@link Beneficiary} with fake details
     */
    public static Beneficiary beneficiary() {
        return new BeneficiaryBuilder().build();
    }

    /**
     * Produces a synthetic transfer transaction from a source account to a beneficiary.
     *
     * @param from   the source account
     * @param to     the destination beneficiary
     * @param amount the transfer amount as a string (e.g. {@code "25.00"})
     * @return a {@link Transaction} linking the two parties
     */
    public static Transaction transfer(Account from, Beneficiary to, String amount) {
        return new TransactionBuilder()
                .from(from)
                .to(to)
                .amount(amount)
                .reference("Test transfer")
                .build();
    }

    /**
     * Produces a synthetic beneficiary with a <strong>per-run unique</strong> natural key,
     * so a re-runnable "create beneficiary" scenario never collides with a prior run's entity.
     *
     * <p>The name, account number and IBAN are all unique within the run yet remain obviously
     * synthetic: the account number stays in the fake numeric range and the IBAN keeps the
     * fake {@code GB00TEST} prefix. Card PANs are unaffected and stay deterministic-from-fake-BIN
     * (the PAN is not the CRUD collision key).</p>
     *
     * @return a {@link Beneficiary} with a fresh, synthetic, run-unique key
     * @see UniqueData
     */
    public static Beneficiary uniqueBeneficiary() {
        return uniqueBeneficiary("Ada Testwell");
    }

    /**
     * Produces a synthetic beneficiary with a per-run unique natural key, using the supplied
     * human-readable base name.
     *
     * <p>The supplied {@code namePrefix} is suffixed with a unique run token (and sanitized);
     * the account number and IBAN are independently made unique while staying clearly synthetic
     * (fake numeric range; fake {@code GB00TEST} IBAN prefix). A {@code null}/blank prefix falls
     * back to a default base.</p>
     *
     * @param namePrefix the human-readable base name for the beneficiary (e.g. {@code "Ada Testwell"})
     * @return a {@link Beneficiary} with a fresh, synthetic, run-unique key
     * @see UniqueData
     */
    public static Beneficiary uniqueBeneficiary(String namePrefix) {
        // Numeric-only token: stays unique across parallel/sharded JVMs even with no
        // TEST_RUN_ID configured (the per-run numeric seed differs per JVM), unlike stripping
        // non-digits from token() which would discard a fallback base-36 run id's letters.
        String uniqueDigits = UniqueData.numericToken();
        return new BeneficiaryBuilder()
                .name(UniqueData.name(namePrefix))
                .accountNumber(syntheticAccountNumber("9", uniqueDigits))
                .iban("GB00TEST" + leftPadDigits(uniqueDigits, 16))
                .build();
    }

    /**
     * Produces a synthetic checking account with a <strong>per-run unique</strong> natural key,
     * so a re-runnable "create account" scenario never collides with a prior run's entity.
     *
     * <p>The account number and holder name are unique within the run yet remain obviously
     * synthetic (fake numeric range; fake holder name with a unique suffix).</p>
     *
     * @return an {@link Account} of type {@code CHECKING} with a fresh, synthetic, run-unique key
     * @see UniqueData
     */
    public static Account uniqueCheckingAccount() {
        // Numeric-only token: collision-free across parallel/sharded JVMs without TEST_RUN_ID
        // (see UniqueData.numericToken). The holder name still uses the full alphanumeric run
        // id for human traceability.
        String uniqueDigits = UniqueData.numericToken();
        return new AccountBuilder()
                .accountType("CHECKING")
                .currency("USD")
                .balance("1000.00")
                .accountNumber(syntheticAccountNumber("1", uniqueDigits))
                .holderName(UniqueData.name("Ada Testwell"))
                .build();
    }

    /**
     * Builds a 16-digit, clearly-synthetic account number: a leading marker digit (so the value
     * stays in the fake range) followed by the run-unique digits, left-padded with zeros.
     *
     * @param leadingMarker a single leading digit identifying the synthetic range
     * @param uniqueDigits  the run-unique numeric suffix
     * @return a 16-digit synthetic account number string
     */
    private static String syntheticAccountNumber(String leadingMarker, String uniqueDigits) {
        return leadingMarker + leftPadDigits(uniqueDigits, 15);
    }

    /**
     * Left-pads a numeric string with {@code '0'} to {@code width}; if it is longer than
     * {@code width} the trailing {@code width} digits are kept.
     */
    private static String leftPadDigits(String digits, int width) {
        String safe = (digits == null) ? "" : digits;
        if (safe.length() > width) {
            return safe.substring(safe.length() - width);
        }
        return StringUtils.leftPad(safe, width, '0');
    }
}
