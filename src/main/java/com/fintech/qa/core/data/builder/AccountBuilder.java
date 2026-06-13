package com.fintech.qa.core.data.builder;

import com.fintech.qa.core.data.model.Account;

/**
 * Fluent builder for {@link Account} test data.
 *
 * <p>Account numbers are synthetic only. Sensible synthetic defaults are provided so a
 * bare {@code new AccountBuilder().build()} yields a usable checking account.</p>
 */
public final class AccountBuilder {

    private String accountNumber = "1000000000007421";
    private String accountType = "CHECKING";
    private String currency = "USD";
    private String balance = "1000.00";
    private String holderName = "Ada Testwell";

    /**
     * @param accountNumber the synthetic account number
     * @return this builder
     */
    public AccountBuilder accountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
        return this;
    }

    /**
     * @param accountType e.g. {@code "CHECKING"}, {@code "SAVINGS"}
     * @return this builder
     */
    public AccountBuilder accountType(String accountType) {
        this.accountType = accountType;
        return this;
    }

    /**
     * @param currency the ISO currency code
     * @return this builder
     */
    public AccountBuilder currency(String currency) {
        this.currency = currency;
        return this;
    }

    /**
     * @param balance the balance as a string
     * @return this builder
     */
    public AccountBuilder balance(String balance) {
        this.balance = balance;
        return this;
    }

    /**
     * @param holderName the fake account holder name
     * @return this builder
     */
    public AccountBuilder holderName(String holderName) {
        this.holderName = holderName;
        return this;
    }

    /**
     * Builds the immutable {@link Account}.
     *
     * @return a new {@link Account}
     */
    public Account build() {
        return new Account(accountNumber, accountType, currency, balance, holderName);
    }
}
