package com.fintech.qa.core.data.model;

import com.fintech.qa.core.security.MaskingUtil;

import java.util.Objects;

/**
 * Immutable representation of a <strong>synthetic money-transfer transaction</strong>
 * between a source {@link Account} and a destination {@link Beneficiary}.
 *
 * <p>Holds no real PII. Display getters mask the underlying account/beneficiary numbers
 * via {@link MaskingUtil} and must be used for any log/report text.</p>
 */
public final class Transaction {

    private final Account from;
    private final Beneficiary to;
    private final String amount;
    private final String currency;
    private final String reference;

    /**
     * Creates a transaction. Prefer
     * {@link com.fintech.qa.core.data.builder.TransactionBuilder} or
     * {@link com.fintech.qa.core.data.TestDataFactory#transfer}.
     *
     * @param from      the source account
     * @param to        the destination beneficiary
     * @param amount    the transfer amount as a string (e.g. {@code "25.00"})
     * @param currency  ISO currency code
     * @param reference a free-text reference/memo
     */
    public Transaction(Account from, Beneficiary to, String amount, String currency, String reference) {
        this.from = from;
        this.to = to;
        this.amount = amount;
        this.currency = currency;
        this.reference = reference;
    }

    /**
     * @return the source account
     */
    public Account getFrom() {
        return from;
    }

    /**
     * @return the destination beneficiary
     */
    public Beneficiary getTo() {
        return to;
    }

    /**
     * @return the transfer amount as a string
     */
    public String getAmount() {
        return amount;
    }

    /**
     * @return the ISO currency code
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * @return the free-text reference/memo
     */
    public String getReference() {
        return reference;
    }

    /**
     * Display-safe amount with currency, e.g. {@code "USD 25.00"}.
     *
     * @return the formatted amount
     */
    public String getDisplayAmount() {
        return (currency == null ? "" : currency + " ") + amount;
    }

    /**
     * Masked source account number (last four kept), or {@code null} if no source set.
     *
     * @return the masked source account number
     */
    public String getMaskedFromAccount() {
        return from == null ? null : from.getMaskedAccountNumber();
    }

    /**
     * Masked destination account number (last four kept), or {@code null} if no
     * beneficiary set.
     *
     * @return the masked destination account number
     */
    public String getMaskedToAccount() {
        return to == null ? null : to.getMaskedAccountNumber();
    }

    /**
     * A fully masked, display-safe one-line summary suitable for logs/reports.
     *
     * @return the masked transaction summary
     */
    public String getMaskedSummary() {
        return MaskingUtil.mask(
                "Transfer " + getDisplayAmount()
                        + " from " + getMaskedFromAccount()
                        + " to " + (to == null ? null : to.getName()) + " " + getMaskedToAccount()
                        + (reference == null ? "" : " ref=" + reference));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Renders only masked account numbers.</p>
     */
    @Override
    public String toString() {
        return "Transaction{" + getMaskedSummary() + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Transaction other)) {
            return false;
        }
        return Objects.equals(from, other.from)
                && Objects.equals(to, other.to)
                && Objects.equals(amount, other.amount)
                && Objects.equals(currency, other.currency)
                && Objects.equals(reference, other.reference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, amount, currency, reference);
    }
}
