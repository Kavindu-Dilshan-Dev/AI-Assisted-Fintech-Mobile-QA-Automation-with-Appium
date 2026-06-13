package com.fintech.qa.core.data.builder;

import com.fintech.qa.core.data.model.Beneficiary;

/**
 * Fluent builder for {@link Beneficiary} test data.
 *
 * <p>Names, account numbers and IBANs are synthetic only. Defaults mirror the first
 * fixture in {@code src/test/resources/testdata/beneficiaries.json}.</p>
 */
public final class BeneficiaryBuilder {

    private String name = "Ada Testwell";
    private String nickname = "Ada (savings)";
    private String bankName = "Sample Test Bank";
    private String accountNumber = "9000000000007421";
    private String iban = "GB00TEST0000000000007421";
    private String currency = "USD";

    /**
     * @param name the fake beneficiary name
     * @return this builder
     */
    public BeneficiaryBuilder name(String name) {
        this.name = name;
        return this;
    }

    /**
     * @param nickname the display nickname
     * @return this builder
     */
    public BeneficiaryBuilder nickname(String nickname) {
        this.nickname = nickname;
        return this;
    }

    /**
     * @param bankName the fake bank name
     * @return this builder
     */
    public BeneficiaryBuilder bankName(String bankName) {
        this.bankName = bankName;
        return this;
    }

    /**
     * @param accountNumber the synthetic account number
     * @return this builder
     */
    public BeneficiaryBuilder accountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
        return this;
    }

    /**
     * @param iban the synthetic IBAN
     * @return this builder
     */
    public BeneficiaryBuilder iban(String iban) {
        this.iban = iban;
        return this;
    }

    /**
     * @param currency the ISO currency code
     * @return this builder
     */
    public BeneficiaryBuilder currency(String currency) {
        this.currency = currency;
        return this;
    }

    /**
     * Builds the immutable {@link Beneficiary}.
     *
     * @return a new {@link Beneficiary}
     */
    public Beneficiary build() {
        return new Beneficiary(name, nickname, bankName, accountNumber, iban, currency);
    }
}
