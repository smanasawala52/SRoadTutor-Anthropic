package com.sroadtutor.phone.dto;

import com.sroadtutor.phone.model.PhoneOwnerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Body for {@code POST /api/phone-numbers} (create) and
 * {@code PUT /api/phone-numbers/{id}} (update).
 *
 * <p>{@code ownerType} + {@code ownerId} are honoured only on create — the FK
 * column on the row is immutable once persisted (changing owner = create new,
 * delete old). On update, both fields are ignored to avoid silent migrations.</p>
 *
 * <p>{@code makePrimary} is optional. When true, the service demotes any
 * existing primary on the same owner in the same transaction so the partial
 * unique index never sees two primaries.</p>
 */
public record PhoneNumberRequest(
        @NotNull PhoneOwnerType ownerType,
        @NotNull UUID ownerId,
        @NotBlank @Size(max = 4)  String countryCode,
        @NotBlank @Size(max = 20) String nationalNumber,
        @Size(max = 40) String label,
        Boolean isWhatsapp,
        Boolean whatsappOptIn,
        Boolean makePrimary
) {}
