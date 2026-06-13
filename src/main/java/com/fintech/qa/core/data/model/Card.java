package com.fintech.qa.core.data.model;

import com.fintech.qa.core.security.MaskingUtil;

import java.util.Objects;

/**
 * Immutable representation of a <strong>synthetic test payment card</strong>.
 *
 * <p>All instances are Luhn-valid numbers built from clearly fake test BINs (e.g. the
 * {@code 400000} test range). This type never carries real PANs or real PII. Display
 * getters always return masked values via {@link MaskingUtil}; the raw PAN/CVV are
 * accessible only for driving the app under test and must never be logged directly.</p>
 */
public final class Card {

    private final String pan;
    private final String cvv;
    private final String expiryMonth;
    private final String expiryYear;
    private final String holderName;

    /**
     * Creates a card. Prefer the
     * {@link com.fintech.qa.core.data.builder.CardBuilder} or
     * {@link com.fintech.qa.core.data.TestDataFactory} factory methods.
     *
     * @param pan         the (synthetic, Luhn-valid) primary account number
     * @param cvv         the (synthetic) card verification value
     * @param expiryMonth two-digit expiry month, e.g. {@code "12"}
     * @param expiryYear  expiry year, e.g. {@code "2030"}
     * @param holderName  the (fake) cardholder name
     */
    public Card(String pan, String cvv, String expiryMonth, String expiryYear, String holderName) {
        this.pan = pan;
        this.cvv = cvv;
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;
        this.holderName = holderName;
    }

    /**
     * Returns the raw synthetic PAN. <strong>Never log this value directly</strong>;
     * use {@link #getMaskedPan()} for any log/report/screenshot text.
     *
     * @return the raw PAN
     */
    public String getPan() {
        return pan;
    }

    /**
     * Returns the raw synthetic CVV. <strong>Never log this value directly.</strong>
     *
     * @return the raw CVV
     */
    public String getCvv() {
        return cvv;
    }

    /**
     * @return the two-digit expiry month
     */
    public String getExpiryMonth() {
        return expiryMonth;
    }

    /**
     * @return the expiry year
     */
    public String getExpiryYear() {
        return expiryYear;
    }

    /**
     * @return the fake cardholder name
     */
    public String getHolderName() {
        return holderName;
    }

    /**
     * @return the expiry formatted as {@code MM/YY}
     */
    public String getExpiry() {
        String yy = expiryYear == null ? "" : expiryYear.substring(Math.max(0, expiryYear.length() - 2));
        return expiryMonth + "/" + yy;
    }

    /**
     * @return the first six digits (the BIN) of the synthetic PAN, or the whole PAN
     *         if shorter than six digits
     */
    public String getBin() {
        if (pan == null) {
            return null;
        }
        return pan.length() >= 6 ? pan.substring(0, 6) : pan;
    }

    /**
     * Masked, display-safe PAN such as {@code "**** **** **** 1234"}.
     *
     * @return the masked card number
     */
    public String getMaskedPan() {
        return MaskingUtil.maskCardNumber(pan);
    }

    /**
     * Masked CVV (fully redacted) suitable for any log/report line.
     *
     * @return the masked CVV
     */
    public String getMaskedCvv() {
        return MaskingUtil.mask(cvv);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Intentionally renders only the <em>masked</em> PAN so a stray
     * {@code toString()} in a log never leaks a synthetic-but-realistic number.</p>
     */
    @Override
    public String toString() {
        return "Card{pan=" + getMaskedPan()
                + ", expiry=" + getExpiry()
                + ", holder=" + holderName + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Card other)) {
            return false;
        }
        return Objects.equals(pan, other.pan)
                && Objects.equals(cvv, other.cvv)
                && Objects.equals(expiryMonth, other.expiryMonth)
                && Objects.equals(expiryYear, other.expiryYear)
                && Objects.equals(holderName, other.holderName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pan, cvv, expiryMonth, expiryYear, holderName);
    }
}
