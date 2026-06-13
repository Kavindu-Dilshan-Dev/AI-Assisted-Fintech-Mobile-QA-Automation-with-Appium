package com.fintech.qa.core.data.model;

import com.fintech.qa.core.security.MaskingUtil;

import java.util.Objects;

/**
 * Immutable representation of a <strong>synthetic test bank account</strong>.
 *
 * <p>Account numbers are synthetic and never real PII. Display getters return masked
 * values (last 4 kept) via {@link MaskingUtil} and must be used for any log/report text.</p>
 */
public final class Account {

    private final String accountNumber;
    private final String accountType;
    private final String currency;
    private final String balance;
    private final String holderName;

    /**
     * Creates an account. Prefer
     * {@link com.fintech.qa.core.data.builder.AccountBuilder} or
     * {@link com.fintech.qa.core.data.TestDataFactory}.
     *
     * @param accountNumber the synthetic account number
     * @param accountType   e.g. {@code "CHECKING"}, {@code "SAVINGS"}
     * @param currency      ISO currency code, e.g. {@code "USD"}
     * @param balance       the balance as a string (e.g. {@code "1000.00"})
     * @param holderName    the fake account holder name
     */
    public Account(String accountNumber, String accountType, String currency, String balance, String holderName) {
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.currency = currency;
        this.balance = balance;
        this.holderName = holderName;
    }

    /**
     * Returns the raw synthetic account number. <strong>Never log directly</strong>;
     * use {@link #getMaskedAccountNumber()} for any log/report/screenshot text.
     *
     * @return the raw account number
     */
    public String getAccountNumber() {
        return accountNumber;
    }

    /**
     * @return the account type (e.g. {@code "CHECKING"})
     */
    public String getAccountType() {
        return accountType;
    }

    /**
     * @return the ISO currency code
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * @return the raw balance string
     */
    public String getBalance() {
        return balance;
    }

    /**
     * @return the fake account holder name
     */
    public String getHolderName() {
        return holderName;
    }

    /**
     * Masked, display-safe account number keeping only the last four digits.
     *
     * @return the masked account number
     */
    public String getMaskedAccountNumber() {
        return MaskingUtil.maskAccountNumber(accountNumber);
    }

    /**
     * Display-safe balance with currency, e.g. {@code "USD 1000.00"}.
     *
     * @return the formatted balance
     */
    public String getMaskedBalance() {
        return (currency == null ? "" : currency + " ") + balance;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Renders only the masked account number.</p>
     */
    @Override
    public String toString() {
        return "Account{number=" + getMaskedAccountNumber()
                + ", type=" + accountType
                + ", currency=" + currency
                + ", holder=" + holderName + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Account other)) {
            return false;
        }
        return Objects.equals(accountNumber, other.accountNumber)
                && Objects.equals(accountType, other.accountType)
                && Objects.equals(currency, other.currency)
                && Objects.equals(balance, other.balance)
                && Objects.equals(holderName, other.holderName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountNumber, accountType, currency, balance, holderName);
    }
}
