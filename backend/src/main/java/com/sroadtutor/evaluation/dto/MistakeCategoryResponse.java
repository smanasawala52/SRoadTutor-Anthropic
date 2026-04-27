package com.sroadtutor.evaluation.dto;

import com.sroadtutor.evaluation.model.MistakeCategory;

import java.util.UUID;

public record MistakeCategoryResponse(
        UUID id,
        String jurisdiction,
        String categoryName,
        String severity,
        int displayOrder,
        boolean active,
        int points,
        String sourceCode
) {

    public static MistakeCategoryResponse fromEntity(MistakeCategory c) {
        return new MistakeCategoryResponse(
                c.getId(),
                c.getJurisdiction(),
                c.getCategoryName(),
                c.getSeverity(),
                c.getDisplayOrder(),
                c.isActive(),
                c.getPoints(),
                c.getSourceCode()
        );
    }
}
