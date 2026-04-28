package com.sroadtutor.insurance.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Partnered insurance broker. Backed by the {@code insurance_brokers}
 * table from V13.
 *
 * <p>Mirrors {@link com.sroadtutor.marketplace.model.Dealership} in shape —
 * platform-admin seeded for V1; owners can READ the active roster but not
 * create / edit. CRM API key is plain text in V1 and tracked as TD for
 * column-level encryption.</p>
 *
 * <p>{@code province=null} means "nationwide" — the lead-routing service
 * treats nationwide brokers as a fallback when no province-specific match
 * exists.</p>
 */
@Entity
@Table(name = "insurance_brokers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsuranceBroker {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    /** 2-letter province code; null = nationwide. */
    @Column(name = "province", length = 8)
    private String province;

    /** Encrypted-at-rest in a future PR; plain text in V1. */
    @Column(name = "crm_api_key_encrypted", length = 500)
    private String crmApiKeyEncrypted;

    @Column(name = "bounty_per_quote", precision = 12, scale = 2)
    private BigDecimal bountyPerQuote;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }
}
