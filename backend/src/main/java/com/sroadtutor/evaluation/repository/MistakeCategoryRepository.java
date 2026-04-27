package com.sroadtutor.evaluation.repository;

import com.sroadtutor.evaluation.model.MistakeCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MistakeCategoryRepository extends JpaRepository<MistakeCategory, UUID> {

    List<MistakeCategory> findByJurisdictionAndActiveTrueOrderByDisplayOrderAsc(String jurisdiction);

    List<MistakeCategory> findByJurisdictionOrderByDisplayOrderAsc(String jurisdiction);
}
