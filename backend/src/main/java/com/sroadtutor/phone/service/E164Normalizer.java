package com.sroadtutor.phone.service;

import com.sroadtutor.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Light-weight E.164 normalizer. Real phone parsing belongs in libphonenumber,
 * but pulling that dependency in just for PR4 is a sledgehammer for a nut —
 * we own the inputs (CRUD-fed, never inbound from a webhook in V1) and the
 * canonical NA path is the dominant case. We support the general E.164 shape
 * for international numbers but do NOT validate national-prefix rules per
 * country.
 *
 * <p>Validation rules:
 * <ul>
 *   <li>{@code countryCode} — 1..3 digits, no leading "+", first digit non-zero.</li>
 *   <li>{@code nationalNumber} — 4..14 digits; total (country+national) max 15.</li>
 *   <li>Both must contain only ASCII digits — caller is expected to strip
 *       formatting (spaces, dashes, parens) on the SPA side; we reject anything
 *       non-numeric to avoid silent normalisation surprises.</li>
 * </ul>
 *
 * <p>Output: a {@link Normalized} record carrying the cleaned country code,
 * national number, and the joined {@code +<country><national>} E.164 form.</p>
 */
@Component
public class E164Normalizer {

    private static final Pattern COUNTRY_CODE = Pattern.compile("^[1-9][0-9]{0,2}$");
    private static final Pattern NATIONAL     = Pattern.compile("^[0-9]{4,14}$");

    /** Total-digit guard per ITU-T E.164 (max 15 digits). */
    private static final int MAX_TOTAL_DIGITS = 15;

    public Normalized normalize(String countryCode, String nationalNumber) {
        if (countryCode == null || nationalNumber == null) {
            throw new BadRequestException(
                    "INVALID_PHONE_NUMBER",
                    "countryCode and nationalNumber are both required");
        }

        String cc = countryCode.trim();
        String nn = nationalNumber.trim();

        if (!COUNTRY_CODE.matcher(cc).matches()) {
            throw new BadRequestException(
                    "INVALID_PHONE_NUMBER",
                    "countryCode must be 1-3 digits with no leading zero (got: " + countryCode + ")");
        }
        if (!NATIONAL.matcher(nn).matches()) {
            throw new BadRequestException(
                    "INVALID_PHONE_NUMBER",
                    "nationalNumber must be 4-14 digits, no formatting characters");
        }
        if (cc.length() + nn.length() > MAX_TOTAL_DIGITS) {
            throw new BadRequestException(
                    "INVALID_PHONE_NUMBER",
                    "Total phone digits must not exceed 15 (E.164)");
        }

        return new Normalized(cc, nn, "+" + cc + nn);
    }

    /** Normalised phone parts. {@code e164} is "+" + countryCode + nationalNumber. */
    public record Normalized(String countryCode, String nationalNumber, String e164) {}
}
