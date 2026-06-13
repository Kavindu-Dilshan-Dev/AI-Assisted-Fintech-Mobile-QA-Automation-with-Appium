package com.fintech.qa.core.data.builder;

import com.fintech.qa.core.data.LuhnGenerator;
import com.fintech.qa.core.data.model.Card;

/**
 * Fluent builder for {@link Card} test data.
 *
 * <p>Defaults to a clearly fake test BIN in the {@code 400000} test range and generates
 * a Luhn-valid 16-digit synthetic PAN. Never use real issuer BINs or real PANs.</p>
 *
 * <pre>{@code
 * Card c = new CardBuilder().bin("400000").holderName("Ada Testwell").build();
 * }</pre>
 */
public final class CardBuilder {

    /** Clearly fake test BIN (Visa test range), NOT a real issuer BIN. */
    public static final String DEFAULT_TEST_BIN = "400000";

    /** Standard PAN length for the synthetic 16-digit cards. */
    public static final int DEFAULT_PAN_LENGTH = 16;

    private String bin = DEFAULT_TEST_BIN;
    private int panLength = DEFAULT_PAN_LENGTH;
    private String pan;
    private String cvv = "123";
    private String expiryMonth = "12";
    private String expiryYear = "2030";
    private String holderName = "Ada Testwell";

    /**
     * Sets the (fake test) BIN prefix used to generate the PAN. Ignored if an explicit
     * PAN is supplied via {@link #pan(String)}.
     *
     * @param bin the fake test BIN prefix
     * @return this builder
     */
    public CardBuilder bin(String bin) {
        this.bin = bin;
        return this;
    }

    /**
     * Sets the total PAN length to generate. Ignored if an explicit PAN is supplied.
     *
     * @param panLength the total PAN length (including check digit)
     * @return this builder
     */
    public CardBuilder panLength(int panLength) {
        this.panLength = panLength;
        return this;
    }

    /**
     * Supplies an explicit (synthetic) PAN, bypassing BIN-based generation.
     *
     * @param pan the synthetic PAN
     * @return this builder
     */
    public CardBuilder pan(String pan) {
        this.pan = pan;
        return this;
    }

    /**
     * @param cvv the synthetic CVV
     * @return this builder
     */
    public CardBuilder cvv(String cvv) {
        this.cvv = cvv;
        return this;
    }

    /**
     * @param expiryMonth the two-digit expiry month
     * @return this builder
     */
    public CardBuilder expiryMonth(String expiryMonth) {
        this.expiryMonth = expiryMonth;
        return this;
    }

    /**
     * @param expiryYear the expiry year
     * @return this builder
     */
    public CardBuilder expiryYear(String expiryYear) {
        this.expiryYear = expiryYear;
        return this;
    }

    /**
     * @param holderName the fake cardholder name
     * @return this builder
     */
    public CardBuilder holderName(String holderName) {
        this.holderName = holderName;
        return this;
    }

    /**
     * Builds the {@link Card}. If no explicit PAN was supplied, generates a Luhn-valid
     * synthetic PAN from the configured fake test BIN.
     *
     * @return a new immutable {@link Card}
     */
    public Card build() {
        String resolvedPan = (pan != null) ? pan : LuhnGenerator.generate(bin, panLength);
        return new Card(resolvedPan, cvv, expiryMonth, expiryYear, holderName);
    }
}
