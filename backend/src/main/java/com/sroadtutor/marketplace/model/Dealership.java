package com.sroadtutor.marketplace.model;

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
 * Partnered dealership. Backed by {@code dealerships} from V1.
 *
 * <p>V1 ships with admin-side seeding only — owners can READ the active
 * roster but not create / edit. Dealership API-key columns are encrypted
 * at the application layer in PR-marketplace-2; today they're plain text
 * and tracked as TD.</p>
 */
@Entity
@Table(name = "dealerships")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dealership {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "city", length = 100)
    private String city;

    /** 2-letter province code, mirrors {@code schools.province}. Used by routing. */
    @Column(name = "province", length = 8)
    private String province;

    /** DealerSocket | CDK | … free text in V1. */
    @Column(name = "crm_type", length = 64)
    private String crmType;

    /** Encrypted-at-rest in a future PR; plain text in V1. Tracked as TD. */
    @Column(name = "crm_api_key_encrypted", length = 500)
    private String crmApiKeyEncrypted;

    @Column(name = "bounty_per_lead", precision = 12, scale = 2)
    private BigDecimal bountyPerLead;

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
