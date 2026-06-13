package com.fintech.qa.core.data.builder;

import com.fintech.qa.core.data.model.Account;
import com.fintech.qa.core.data.model.Beneficiary;
import com.fintech.qa.core.data.model.Transaction;

/**
 * Fluent builder for {@link Transaction} test data.
 *
 * <p>If no explicit currency is supplied at {@link #build()} time, the currency is
 * inferred from the source {@link Account}, then the destination {@link Beneficiary},
 * defaulting to {@code "USD"}.</p>
 */
public final class TransactionBuilder {

    private Account from;
    private Beneficiary to;
    private String amount = "25.00";
    private String currency;
    private String reference = "Test transfer";

    /**
     * @param from the source account
     * @return this builder
     */
    public TransactionBuilder from(Account from) {
        this.from = from;
        return this;
    }

    /**
     * @param to the destination beneficiary
     * @return this builder
     */
    public TransactionBuilder to(Beneficiary to) {
        this.to = to;
        return this;
    }

    /**
     * @param amount the transfer amount as a string
     * @return this builder
     */
    public TransactionBuilder amount(String amount) {
        this.amount = amount;
        return this;
    }

    /**
     * @param currency the ISO currency code; if left unset it is inferred at build time
     * @return this builder
     */
    public TransactionBuilder currency(String currency) {
        this.currency = currency;
        return this;
    }

    /**
     * @param reference the free-text reference/memo
     * @return this builder
     */
    public TransactionBuilder reference(String reference) {
        this.reference = reference;
        return this;
    }

    /**
     * Builds the immutable {@link Transaction}, inferring the currency from the source
     * account or destination beneficiary when none was explicitly set.
     *
     * @return a new {@link Transaction}
     */
    public Transaction build() {
        String resolvedCurrency = currency;
        if (resolvedCurrency == null && from != null) {
            resolvedCurrency = from.getCurrency();
        }
        if (resolvedCurrency == null && to != null) {
            resolvedCurrency = to.getCurrency();
        }
        if (resolvedCurrency == null) {
            resolvedCurrency = "USD";
        }
        return new Transaction(from, to, amount, resolvedCurrency, reference);
    }
}
