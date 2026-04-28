package com.sroadtutor.risk.dto;

import com.sroadtutor.risk.model.RiskScore;

import java.time.Instant;
import java.util.UUID;

public record RiskScoreResponse(
        UUID id,
        String studentAnonymizedHash,
        String mistakeProfileJson,
        String riskTier,
        Instant generatedAt,
        String licensedToInsurer
) {

    public static RiskScoreResponse fromEntity(RiskScore r) {
        return new RiskScoreResponse(
                r.getId(),
                r.getStudentAnonymizedHash(),
                r.getMistakeProfileJson(),
                r.getRiskTier(),
                r.getGeneratedAt(),
                r.getLicensedToInsurer()
        );
    }
}
