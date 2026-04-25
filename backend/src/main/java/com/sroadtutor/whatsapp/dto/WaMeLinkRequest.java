package com.sroadtutor.whatsapp.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * Body for {@code POST /api/whatsapp/links}. Either:
 * <ul>
 *   <li>{@code templateId} — pick an existing reusable body. {@code placeholders}
 *       substitutes {@code {{key}}} tokens before logging the rendered body.</li>
 *   <li>{@code body} — free-text message. Used when no template fits.</li>
 * </ul>
 *
 * <p>If both are supplied, {@code templateId} wins. If neither is supplied, the
 * service throws {@code MISSING_BODY_OR_TEMPLATE} (400).</p>
 *
 * <p>{@code correlationId} is a caller-supplied tag (e.g. {@code "lesson:abc123"})
 * for grouping audit-log rows. Optional.</p>
 */
public record WaMeLinkRequest(
        @NotNull UUID recipientPhoneId,
        UUID templateId,
        Map<String, String> placeholders,
        @Size(max = 4000) String body,
        @Size(max = 64) String correlationId
) {}
