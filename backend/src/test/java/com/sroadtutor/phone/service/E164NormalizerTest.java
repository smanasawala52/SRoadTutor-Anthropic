package com.sroadtutor.phone.service;

import com.sroadtutor.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link E164Normalizer}. No Spring, no DB — pure validation logic.
 */
class E164NormalizerTest {

    private final E164Normalizer normalizer = new E164Normalizer();

    // ------------------------- happy paths -------------------------

    @Test
    void normalize_acceptsCanonicalNorthAmerican() {
        E164Normalizer.Normalized n = normalizer.normalize("1", "3065551234");

        assertThat(n.countryCode()).isEqualTo("1");
        assertThat(n.nationalNumber()).isEqualTo("3065551234");
        assertThat(n.e164()).isEqualTo("+13065551234");
    }

    @Test
    void normalize_acceptsThreeDigitCountryCode() {
        E164Normalizer.Normalized n = normalizer.normalize("880", "1712345678");

        assertThat(n.countryCode()).isEqualTo("880");
        assertThat(n.e164()).isEqualTo("+8801712345678");
    }

    @Test
    void normalize_acceptsTwoDigitCountryCode() {
        E164Normalizer.Normalized n = normalizer.normalize("44", "7700900123");

        assertThat(n.countryCode()).isEqualTo("44");
        assertThat(n.e164()).isEqualTo("+447700900123");
    }

    @Test
    void normalize_trimsLeadingTrailingWhitespace() {
        E164Normalizer.Normalized n = normalizer.normalize(" 1 ", " 3065551234 ");

        assertThat(n.countryCode()).isEqualTo("1");
        assertThat(n.nationalNumber()).isEqualTo("3065551234");
        assertThat(n.e164()).isEqualTo("+13065551234");
    }

    @Test
    void normalize_acceptsMaxLengthAtBoundary() {
        // 1-digit CC + 14-digit national = 15 total — the E.164 maximum.
        E164Normalizer.Normalized n = normalizer.normalize("1", "23456789012345");
        assertThat(n.e164()).isEqualTo("+123456789012345");
    }

    @Test
    void normalize_acceptsMinLengthNational() {
        // 4 digits is the minimum the regex permits.
        E164Normalizer.Normalized n = normalizer.normalize("1", "1234");
        assertThat(n.e164()).isEqualTo("+11234");
    }

    // ------------------------- nulls / blanks -------------------------

    @Test
    void normalize_rejectsNullCountryCode() {
        assertThatThrownBy(() -> normalizer.normalize(null, "3065551234"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("required");
    }

    @Test
    void normalize_rejectsNullNationalNumber() {
        assertThatThrownBy(() -> normalizer.normalize("1", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("required");
    }

    // ------------------------- country-code rules -------------------------

    @Test
    void normalize_rejectsLeadingZeroCountryCode() {
        assertThatThrownBy(() -> normalizer.normalize("01", "3065551234"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("countryCode");
    }

    @Test
    void normalize_rejectsFourDigitCountryCode() {
        assertThatThrownBy(() -> normalizer.normalize("1234", "3065551234"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("countryCode");
    }

    @Test
    void normalize_rejectsEmptyCountryCode() {
        assertThatThrownBy(() -> normalizer.normalize("", "3065551234"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("countryCode");
    }

    @Test
    void normalize_rejectsCountryCodeWithPlus() {
        // Caller is expected to strip the leading "+" — wa.me digits never include it.
        assertThatThrownBy(() -> normalizer.normalize("+1", "3065551234"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("countryCode");
    }

    @Test
    void normalize_rejectsCountryCodeWithLetters() {
        assertThatThrownBy(() -> normalizer.normalize("1A", "3065551234"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("countryCode");
    }

    // ------------------------- national-number rules -------------------------

    @Test
    void normalize_rejectsTooShortNational() {
        // 3 digits — below the 4-digit floor.
        assertThatThrownBy(() -> normalizer.normalize("1", "123"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("nationalNumber");
    }

    @Test
    void normalize_rejectsTooLongNational() {
        // 15 digits in the national portion alone — total is ≥16, breaks regex first.
        assertThatThrownBy(() -> normalizer.normalize("1", "123456789012345"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("nationalNumber");
    }

    @Test
    void normalize_rejectsTotalOverFifteenDigits() {
        // 2-digit CC + 14-digit national = 16 total. Each individually passes
        // the regex — only the total-digit guard catches it.
        assertThatThrownBy(() -> normalizer.normalize("12", "12345678901234"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("15");
    }

    @Test
    void normalize_rejectsNationalWithDashes() {
        assertThatThrownBy(() -> normalizer.normalize("1", "306-555-1234"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("nationalNumber");
    }

    @Test
    void normalize_rejectsNationalWithSpaces() {
        assertThatThrownBy(() -> normalizer.normalize("1", "306 555 1234"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("nationalNumber");
    }

    @Test
    void normalize_rejectsNationalWithParens() {
        assertThatThrownBy(() -> normalizer.normalize("1", "(306)5551234"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("nationalNumber");
    }
}
