package com.sroadtutor.whatsapp.service;

import com.sroadtutor.auth.model.Role;
import com.sroadtutor.auth.repository.UserRepository;
import com.sroadtutor.exception.BadRequestException;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.phone.model.PhoneNumber;
import com.sroadtutor.phone.repository.PhoneNumberRepository;
import com.sroadtutor.phone.service.PhoneScopeChecker;
import com.sroadtutor.whatsapp.dto.ClickConfirmResponse;
import com.sroadtutor.whatsapp.dto.WaMeLinkRequest;
import com.sroadtutor.whatsapp.dto.WaMeLinkResponse;
import com.sroadtutor.whatsapp.model.WhatsappMessageLog;
import com.sroadtutor.whatsapp.model.WhatsappTemplate;
import com.sroadtutor.whatsapp.repository.WhatsappMessageLogRepository;
import com.sroadtutor.whatsapp.repository.WhatsappTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates wa.me click-to-chat URLs and audits every link generation +
 * click-confirm (locked at PR4 kickoff: "every link generation + click-confirm").
 *
 * <p>The native WhatsApp Cloud API path (push messages without a click) is
 * tracked as TD-17 and explicitly deferred to V2+. In V1 the SPA renders the
 * returned {@code waMeUrl} as an anchor; when the user clicks, the SPA fires
 * {@link #confirmClick} which flips the recipient phone's {@code verifiedAt}
 * the first time round (D14 — "Trust the typist + WhatsApp click-confirm").</p>
 *
 * <p>Authorization model:
 * <ul>
 *   <li><b>Generate</b> — caller must pass {@link PhoneScopeChecker} for the
 *       recipient phone (the same gate that applies to phone CRUD).</li>
 *   <li><b>Click-confirm</b> — caller must equal the {@code senderUserId} on
 *       the log row (the user who generated the link is the user who clicks).
 *       This implicitly preserves the original scope check at the moment of
 *       generation.</li>
 * </ul>
 */
@Service
public class WaMeService {

    private static final Logger log = LoggerFactory.getLogger(WaMeService.class);

    /** Matches `{{key}}` with optional whitespace inside the braces. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    private final PhoneNumberRepository phoneRepo;
    private final WhatsappTemplateRepository templateRepo;
    private final WhatsappMessageLogRepository logRepo;
    private final PhoneScopeChecker scopeChecker;
    private final UserRepository userRepo;

    public WaMeService(PhoneNumberRepository phoneRepo,
                       WhatsappTemplateRepository templateRepo,
                       WhatsappMessageLogRepository logRepo,
                       PhoneScopeChecker scopeChecker,
                       UserRepository userRepo) {
        this.phoneRepo = phoneRepo;
        this.templateRepo = templateRepo;
        this.logRepo = logRepo;
        this.scopeChecker = scopeChecker;
        this.userRepo = userRepo;
    }

    // ============================================================
    // Generate link
    // ============================================================

    @Transactional
    public WaMeLinkResponse generateLink(Role senderRole, UUID senderUserId, WaMeLinkRequest req) {
        PhoneNumber phone = phoneRepo.findById(req.recipientPhoneId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Recipient phone not found: " + req.recipientPhoneId()));

        // Same scope rules as phone CRUD — if you can't see the phone, you
        // can't message it.
        scopeChecker.requireReadScope(senderRole, senderUserId, phone);

        if (!phone.isWhatsapp() || !phone.isWhatsappOptIn()) {
            throw new BadRequestException(
                    "WHATSAPP_DISABLED",
                    "Recipient phone is not opted-in for WhatsApp messages");
        }

        // Resolve body — template wins if both provided.
        WhatsappTemplate template = null;
        String renderedBody;
        if (req.templateId() != null) {
            template = templateRepo.findById(req.templateId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "WhatsApp template not found: " + req.templateId()));
            if (!template.isActive()) {
                throw new BadRequestException(
                        "TEMPLATE_INACTIVE",
                        "Template " + template.getCode() + " is not active");
            }
            renderedBody = renderPlaceholders(
                    template.getBody(),
                    req.placeholders() == null ? Map.of() : req.placeholders());
        } else if (req.body() != null && !req.body().isBlank()) {
            renderedBody = req.body().trim();
        } else {
            throw new BadRequestException(
                    "MISSING_BODY_OR_TEMPLATE",
                    "Either templateId or body is required");
        }

        // Tenant context — pull the sender's school. May be null for OWNERs
        // without a school yet, and that's fine; the column is nullable.
        UUID senderSchoolId = userRepo.findById(senderUserId)
                .map(u -> u.getSchoolId())
                .orElse(null);

        WhatsappMessageLog logRow = WhatsappMessageLog.builder()
                .senderUserId(senderUserId)
                .recipientPhoneId(phone.getId())
                .templateId(template == null ? null : template.getId())
                .renderedBody(renderedBody)
                .schoolId(senderSchoolId)
                .correlationId(req.correlationId())
                .build();
        logRow = logRepo.save(logRow);

        String url = buildWaMeUrl(phone.getCountryCode() + phone.getNationalNumber(), renderedBody);

        log.info("wa.me link generated id={} sender={} recipient={} template={} correlation={}",
                logRow.getId(), senderUserId, phone.getId(),
                template == null ? "<freetext>" : template.getCode(),
                req.correlationId());

        return WaMeLinkResponse.fromLog(logRow, url);
    }

    // ============================================================
    // Click confirm — flips verifiedAt on first confirmation
    // ============================================================

    @Transactional
    public ClickConfirmResponse confirmClick(UUID currentUserId, UUID logId) {
        WhatsappMessageLog logRow = logRepo.findById(logId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "wa.me log not found: " + logId));

        if (!logRow.getSenderUserId().equals(currentUserId)) {
            // Sender == clicker is the verification model. Anyone else trying
            // to confirm is by definition not the link-generator.
            throw new AccessDeniedException(
                    "Only the link generator can confirm the click");
        }

        Instant clickAt = logRow.getLinkClickedAt();
        if (clickAt == null) {
            clickAt = Instant.now();
            logRow.setLinkClickedAt(clickAt);
            logRepo.save(logRow);
        }

        PhoneNumber phone = phoneRepo.findById(logRow.getRecipientPhoneId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Recipient phone disappeared: " + logRow.getRecipientPhoneId()));

        boolean verifiedNow = false;
        if (phone.getVerifiedAt() == null) {
            phone.setVerifiedAt(clickAt);
            phoneRepo.save(phone);
            verifiedNow = true;
            log.info("Phone {} verified via wa.me click on log {}", phone.getId(), logId);
        }

        return new ClickConfirmResponse(
                logRow.getId(),
                phone.getId(),
                clickAt,
                verifiedNow,
                phone.getVerifiedAt()
        );
    }

    // ============================================================
    // URL construction
    // ============================================================

    /**
     * Build the canonical {@code https://wa.me/<digits>?text=<urlencoded>} URL.
     * The digits portion is the recipient's E.164 with the leading "+" stripped
     * (wa.me's spec) — i.e. {@code countryCode + nationalNumber} concatenated.
     */
    String buildWaMeUrl(String digits, String body) {
        String encoded = URLEncoder.encode(body, StandardCharsets.UTF_8);
        return "https://wa.me/" + digits + "?text=" + encoded;
    }

    /**
     * Substitute {@code {{key}}} tokens in {@code body} from the supplied map.
     * A missing key throws {@code PLACEHOLDER_MISSING} (400) — silent fallback
     * to literal "{{key}}" in the rendered message would be a bug.
     */
    String renderPlaceholders(String body, Map<String, String> placeholders) {
        Matcher m = PLACEHOLDER.matcher(body);
        StringBuilder out = new StringBuilder(body.length() + 32);
        while (m.find()) {
            String key = m.group(1);
            String value = placeholders.get(key);
            if (value == null) {
                throw new BadRequestException(
                        "PLACEHOLDER_MISSING",
                        "Template placeholder '" + key + "' was not supplied");
            }
            // Replacement.quoteReplacement protects against $ or \ in the value.
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }
}
