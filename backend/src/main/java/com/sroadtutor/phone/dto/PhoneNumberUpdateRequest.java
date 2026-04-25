package com.sroadtutor.phone.dto;

import jakarta.validation.constraints.Size;

/**
 * Body for {@code PUT /api/phone-numbers/{id}} — fields-only update. The owner
 * FK is immutable; to "move" a number to a different owner, delete + recreate.
 *
 * <p>All fields are nullable so the SPA can send a partial update. Service-layer
 * applies a field if non-null; leaves the existing value otherwise. {@code countryCode}
 * and {@code nationalNumber} must travel together (changing one without the other
 * is rejected at the service layer).</p>
 */
public record PhoneNumberUpdateRequest(
        @Size(max = 4)  String countryCode,
        @Size(max = 20) String nationalNumber,
        @Size(max = 40) String label,
        Boolean isWhatsapp,
        Boolean whatsappOptIn
) {}
