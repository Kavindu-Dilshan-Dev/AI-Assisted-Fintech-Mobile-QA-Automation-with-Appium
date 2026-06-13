package com.fintech.qa.core.data.model;

import com.fintech.qa.core.security.MaskingUtil;

import java.util.Objects;

/**
 * Immutable representation of a <strong>synthetic transfer beneficiary</strong>.
 *
 * <p>Names are fake; account numbers and IBANs are synthetic and never real PII.
 * Display getters return masked values via {@link MaskingUtil} and must be used for
 * any log/report text. Mirrors the shape of
 * {@code src/test/resources/testdata/beneficiaries.json}.</p>
 */
public final class Beneficiary {

    private final String name;
    private final String nickname;
    private final String bankName;
    private final String accountNumber;
    private final String iban;
    private final String currency;

    /**
     * Creates a beneficiary. Prefer
     * {@link com.fintech.qa.core.data.builder.BeneficiaryBuilder} or
     * {@link com.fintech.qa.core.data.TestDataFactory}.
     *
     * @param name          the fake beneficiary name
     * @param nickname      a display nickname (e.g. {@code "Ada (savings)"})
     * @param bankName      the (fake) bank name
     * @param accountNumber the synthetic account number
     * @param iban          the synthetic IBAN
     * @param currency      ISO currency code
     */
    public Beneficiary(String name, String nickname, String bankName,
                       String accountNumber, String iban, String currency) {
        this.name = name;
        this.nickname = nickname;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.iban = iban;
        this.currency = currency;
    }

    /**
     * @return the fake beneficiary name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the display nickname
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * @return the fake bank name
     */
    public String getBankName() {
        return bankName;
    }

    /**
     * Returns the raw synthetic account number. <strong>Never log directly</strong>;
     * use {@link #getMaskedAccountNumber()}.
     *
     * @return the raw account number
     */
    public String getAccountNumber() {
        return accountNumber;
    }

    /**
     * Returns the raw synthetic IBAN. <strong>Never log directly</strong>;
     * use {@link #getMaskedIban()}.
     *
     * @return the raw IBAN
     */
    public String getIban() {
        return iban;
    }

    /**
     * @return the ISO currency code
     */
    public String getCurrency() {
        return currency;
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
     * Masked, display-safe IBAN (keeps last four digits via the masking engine).
     *
     * @return the masked IBAN
     */
    public String getMaskedIban() {
        return MaskingUtil.mask(iban);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Renders only the masked account number.</p>
     */
    @Override
    public String toString() {
        return "Beneficiary{name=" + name
                + ", bank=" + bankName
                + ", account=" + getMaskedAccountNumber()
                + ", currency=" + currency + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Beneficiary other)) {
            return false;
        }
        return Objects.equals(name, other.name)
                && Objects.equals(nickname, other.nickname)
                && Objects.equals(bankName, other.bankName)
                && Objects.equals(accountNumber, other.accountNumber)
                && Objects.equals(iban, other.iban)
                && Objects.equals(currency, other.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, nickname, bankName, accountNumber, iban, currency);
    }
}
