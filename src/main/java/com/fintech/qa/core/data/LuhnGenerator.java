package com.fintech.qa.core.data;

import org.apache.commons.lang3.StringUtils;

import java.util.Random;

/**
 * Generates and validates Luhn (mod-10) compliant card numbers.
 *
 * <p>This utility is used to build <strong>synthetic, clearly fake</strong> test card
 * numbers. It is never used with, and must never be seeded by, real PANs or real
 * issuer BINs. A deterministic seeded {@link java.util.Random} keeps generated
 * numbers reproducible across runs for stable test data.</p>
 */
public final class LuhnGenerator {

    /** Fixed seed so generated test cards are deterministic and reproducible. */
    private static final long SEED = 0x5DEECE66DL;

    private LuhnGenerator() {
        // utility class
    }

    /**
     * Generates a Luhn-valid numeric string starting with the supplied prefix.
     *
     * @param binPrefix the leading digits (e.g. a fake test BIN such as {@code "400000"});
     *                  must be non-blank and contain digits only
     * @param length    the total desired length of the resulting number (including the
     *                  prefix and the trailing Luhn check digit); must be greater than
     *                  the prefix length
     * @return a Luhn-valid numeric string of exactly {@code length} digits
     * @throws IllegalArgumentException if the prefix is blank/non-numeric or the length
     *                                  is not greater than the prefix length
     */
    public static String generate(String binPrefix, int length) {
        if (StringUtils.isBlank(binPrefix) || !binPrefix.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("binPrefix must be a non-blank numeric string");
        }
        if (length <= binPrefix.length()) {
            throw new IllegalArgumentException(
                    "length must be greater than the prefix length (" + binPrefix.length() + ")");
        }

        // Deterministic generator seeded from the prefix + requested length so each
        // distinct (prefix,length) pair yields a stable, reproducible card number.
        Random random = new Random(SEED ^ binPrefix.hashCode() ^ ((long) length << 16));

        StringBuilder sb = new StringBuilder(binPrefix);
        // Fill all but the final check digit with deterministic random digits.
        while (sb.length() < length - 1) {
            sb.append(random.nextInt(10));
        }
        sb.append(checkDigit(sb.toString()));
        return sb.toString();
    }

    /**
     * Validates that the supplied number satisfies the Luhn (mod-10) checksum.
     *
     * @param number the candidate number; may be {@code null}
     * @return {@code true} if the number is non-blank, all digits, and Luhn-valid
     */
    public static boolean isValid(String number) {
        if (StringUtils.isBlank(number) || !number.chars().allMatch(Character::isDigit)) {
            return false;
        }
        return sumForLuhn(number, false) % 10 == 0;
    }

    /**
     * Computes the Luhn check digit for a partial number (the part WITHOUT the check digit).
     *
     * @param partialNumber the digits preceding the check digit
     * @return the single check digit (0-9) that makes the full number Luhn-valid
     */
    private static int checkDigit(String partialNumber) {
        int sum = sumForLuhn(partialNumber, true);
        return (10 - (sum % 10)) % 10;
    }

    /**
     * Core Luhn summation.
     *
     * @param digits         the digits to sum
     * @param buildingNumber when {@code true} the rightmost digit is treated as the first
     *                       position to be doubled (because the real check digit is not yet
     *                       appended); when {@code false} the rightmost digit is the check
     *                       digit itself and is never doubled
     * @return the Luhn sum
     */
    private static int sumForLuhn(String digits, boolean buildingNumber) {
        int sum = 0;
        boolean doubleDigit = buildingNumber;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleDigit) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleDigit = !doubleDigit;
        }
        return sum;
    }
}
