package com.sroadtutor.phone.dto;

import jakarta.validation.constraints.NotNull;

/** Body for {@code POST /api/phone-numbers/{id}/whatsapp-optin}. */
public record WhatsappOptInRequest(
        @NotNull Boolean optIn
) {}
