package com.sroadtutor.risk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sroadtutor.auth.model.Role;
import com.sroadtutor.config.AppProperties;
import com.sroadtutor.evaluation.dto.ReadinessScoreResponse;
import com.sroadtutor.evaluation.service.MistakeLogService;
import com.sroadtutor.exception.ResourceNotFoundException;
import com.sroadtutor.risk.dto.RiskAggregateResponse;
import com.sroadtutor.risk.dto.RiskScoreResponse;
import com.sroadtutor.risk.model.RiskScore;
import com.sroadtutor.risk.repository.RiskScoreRepository;
import com.sroadtutor.school.model.School;
import com.sroadtutor.school.repository.SchoolRepository;
import com.sroadtutor.student.model.Student;
import com.sroadtutor.student.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * New Driver Risk Score generator + aggregate read. Locked at PR19:
 *
 * <ul>
 *   <li><b>PIPEDA-first</b> — generated rows carry only a SHA-256 hash of
 *       (studentId + jwt-secret-as-salt). The platform retains the salt;
 *       insurers never see it. Re-deriving the studentId requires both
 *       the hash and the salt, so a single-source breach can't link back
 *       to an individual.</li>
 *   <li><b>Tier formula</b> — drives off the readiness score the mistake
 *       logger already computes (last 5 sessions, weighted demerits).
 *       <pre>
 *         readiness ≥ 90 OR no FAIL recently → LOW
 *         readiness ≥ 70                     → MEDIUM
 *         readiness ≥ 50                     → HIGH
 *         readiness &lt; 50  OR  any FAIL recently → CRITICAL
 *       </pre>
 *       The classifier is intentionally simple in V1 — investors will
 *       want a model that's tunable post-hoc.</li>
 *   <li><b>Generate</b> — OWNER of the student's school. One row per
 *       student-graduation. Upsert: re-running for the same hash
 *       overwrites the previous mistake profile + tier.</li>
 *   <li><b>Aggregate</b> — counts per tier across the platform. No
 *       student-level detail. The B2B API key gate is tracked as TD;
 *       V1 keeps the endpoint OWNER-only as a safe stub.</li>
 * </ul>
 */
@Service
public class RiskScoreService {

    private static final Logger log = LoggerFactory.getLogger(RiskScoreService.class);

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule()).build();

    private final RiskScoreRepository riskRepo;
    private final StudentRepository studentRepo;
    private final SchoolRepository schoolRepo;
    private final MistakeLogService mistakeLogService;
    private final String salt;

    public RiskScoreService(RiskScoreRepository riskRepo,
                              StudentRepository studentRepo,
                              SchoolRepository schoolRepo,
                              MistakeLogService mistakeLogService,
                              AppProperties props) {
        this.riskRepo = riskRepo;
        this.studentRepo = studentRepo;
        this.schoolRepo = schoolRepo;
        this.mistakeLogService = mistakeLogService;
        // Re-use the JWT secret as the anonymization salt — guaranteed
        // present + at least 32 bytes by AppProperties validation. The
        // explicit RISK_HASH_SALT env var is tracked as TD when rotating
        // the JWT secret would also rotate the hashes.
        this.salt = props.jwt().secret();
    }

    // ============================================================
    // Generate
    // ============================================================

    @Transactional
    public RiskScoreResponse generateForStudent(Role role, UUID currentUserId, UUID studentId) {
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));
        School school = schoolRepo.findById(student.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "School not found: " + student.getSchoolId()));
        if (role != Role.OWNER || !currentUserId.equals(school.getOwnerId())) {
            throw new AccessDeniedException("Only the school OWNER can generate risk scores");
        }

        // Pull the readiness score the mistake logger already computes —
        // the OWNER scope path is honoured by MistakeLogService.
        ReadinessScoreResponse readiness = mistakeLogService.readinessForStudent(
                role, currentUserId, studentId);
        String tier = classify(readiness);

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("readinessAverage", readiness.averageScore());
        profile.put("sessionsConsidered", readiness.sessionsConsidered());
        profile.put("anyFailRecently", readiness.anyFailMistakeRecently());
        profile.put("schoolJurisdiction", school.getJurisdiction());
        profile.put("province", school.getProvince());

        String hash = anonymize(studentId);
        RiskScore existing = riskRepo.findByStudentAnonymizedHash(hash).orElse(null);
        RiskScore row;
        if (existing == null) {
            row = RiskScore.builder()
                    .studentAnonymizedHash(hash)
                    .mistakeProfileJson(serialize(profile))
                    .riskTier(tier)
                    .generatedAt(Instant.now())
                    .build();
        } else {
            row = existing;
            row.setMistakeProfileJson(serialize(profile));
            row.setRiskTier(tier);
            row.setGeneratedAt(Instant.now());
        }
        row = riskRepo.save(row);

        log.info("Risk score generated for student {} (tier={}, readiness={}, hash={})",
                studentId, tier, readiness.averageScore(), hash.substring(0, 8) + "…");
        return RiskScoreResponse.fromEntity(row);
    }

    // ============================================================
    // Reads
    // ============================================================

    @Transactional(readOnly = true)
    public RiskScoreResponse getByHash(Role role, String hash) {
        // V1: OWNER-only. PR-risk-2 introduces a B2B API key gate that
        // grants insurers their licensed slice.
        if (role != Role.OWNER) {
            throw new AccessDeniedException("Only an OWNER can read individual risk scores in V1");
        }
        RiskScore row = riskRepo.findByStudentAnonymizedHash(hash)
                .orElseThrow(() -> new ResourceNotFoundException("Risk score not found: " + hash));
        return RiskScoreResponse.fromEntity(row);
    }

    @Transactional(readOnly = true)
    public RiskAggregateResponse aggregate(Role role) {
        if (role != Role.OWNER) {
            throw new AccessDeniedException("Only an OWNER can read the risk aggregate in V1");
        }
        Map<String, Long> counts = new HashMap<>();
        long total = 0;
        for (Object[] row : riskRepo.countsByTier()) {
            String tier = (String) row[0];
            long n = ((Number) row[1]).longValue();
            counts.put(tier, n);
            total += n;
        }
        return new RiskAggregateResponse(total, counts, Instant.now());
    }

    // ============================================================
    // Helpers
    // ============================================================

    /**
     * Tier classifier. Order of checks matters:
     * (1) any FAIL → CRITICAL (regardless of average — single FAIL on the
     *     road test is failing under SGI scoring),
     * (2) average score thresholds otherwise.
     */
    private static String classify(ReadinessScoreResponse readiness) {
        if (readiness.anyFailMistakeRecently()) return RiskScore.TIER_CRITICAL;
        double s = readiness.averageScore();
        if (s >= 90.0) return RiskScore.TIER_LOW;
        if (s >= 70.0) return RiskScore.TIER_MEDIUM;
        if (s >= 50.0) return RiskScore.TIER_HIGH;
        return RiskScore.TIER_CRITICAL;
    }

    /** SHA-256(studentId.toString() + salt) as lowercase hex. */
    String anonymize(UUID studentId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(studentId.toString().getBytes(StandardCharsets.UTF_8));
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String serialize(Map<String, Object> m) {
        try {
            return JSON.writeValueAsString(m);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialise mistake profile", ex);
        }
    }
}
