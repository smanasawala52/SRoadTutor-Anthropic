package com.sroadtutor.whatsapp.dto;

import com.sroadtutor.whatsapp.model.WhatsappMessageLog;

import java.time.Instant;
import java.util.UUID;

/**
 * Response for {@code POST /api/whatsapp/links}. {@code logId} is what the SPA
 * fires back at {@code POST /api/whatsapp/links/{logId}/click-confirm} when the
 * user clicks the rendered anchor — that beacon is what flips the recipient
 * phone's {@code verifiedAt} on first confirmation (D14).
 */
public record WaMeLinkResponse(
        UUID logId,
        String waMeUrl,
        String renderedBody,
        UUID recipientPhoneId,
        Instant linkGeneratedAt
) {

    public static WaMeLinkResponse fromLog(WhatsappMessageLog log, String waMeUrl) {
        return new WaMeLinkResponse(
                log.getId(),
                waMeUrl,
                log.getRenderedBody(),
                log.getRecipientPhoneId(),
                log.getLinkGeneratedAt()
        );
    }
}
